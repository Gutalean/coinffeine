package coinffeine.peer.bitcoin

import java.io.{FileInputStream, File}
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import com.google.bitcoin.core.Transaction.SigHash
import com.google.bitcoin.core.{TransactionConfidence, Transaction, AbstractWalletEventListener}
import com.google.bitcoin.store.WalletProtobufSerializer
import com.google.bitcoin.wallet.WalletTransaction

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency._
import coinffeine.model.currency.Implicits._

class SmartWallet(wallet: Wallet) {

  import SmartWallet._

  private val blockedOutputs = {
    val outputs = new BlockedOutputs
    outputs.setSpendCandidates(wallet.calculateAllSpendCandidates(true).toSet)
    outputs
  }

  private var listeners: Set[(Listener, ExecutionContext)] = Set.empty

  def addListener(listener: Listener)(implicit executor: ExecutionContext) = synchronized {
    listeners += listener -> executor
  }

  def loadFromFile(file: File): Unit = {
    val stream = new FileInputStream(file)
    try {
      new WalletProtobufSerializer()
        .readWallet(WalletProtobufSerializer.parseToProto(stream), wallet)
    } finally {
      stream.close()
    }
  }

  def addresses: Seq[Address] = synchronized {
    val network = wallet.getNetworkParameters
    wallet.getKeys.map(_.toAddress(network))
  }

  def balance: BitcoinAmount = synchronized { Currency.Bitcoin.fromSatoshi(wallet.getBalance) }

  def value(tx: MutableTransaction): BitcoinAmount =
    Currency.Bitcoin.fromSatoshi(tx.getValue(wallet))

  def createKeyPair(): KeyPair = synchronized {
    val keyPair = new KeyPair()
    wallet.addKey(keyPair)
    keyPair
  }

  def blockFunds(amount: BitcoinAmount): Option[BlockedCoinsId] = synchronized {
    blockedOutputs.block(amount)
  }

  def unblockFunds(coinsId: BlockedCoinsId): Unit = synchronized {
    blockedOutputs.unblock(coinsId)
  }

  def createTransaction(amount: BitcoinAmount, to: Address): ImmutableTransaction = synchronized {
    require(amount < blockedOutputs.spendable,
      s"cannot create a transaction of $amount: not enough funds in wallet")
    ImmutableTransaction(blockFunds(to, amount))
  }

  def createMultisignTransaction(coinsId: BlockedCoinsId,
                                 amount: BitcoinAmount,
                                 fee: BitcoinAmount,
                                 signatures: Seq[KeyPair]): ImmutableTransaction = synchronized {
    val inputs = blockedOutputs.use(coinsId, amount + fee)
    ImmutableTransaction(blockMultisignFunds(inputs, signatures, amount, fee))
  }

  def releaseTransaction(tx: ImmutableTransaction): Unit = synchronized {
    releaseFunds(tx.get)
    val releasedOutputs = for {
      input <- tx.get.getInputs.toList
      parentTx <- Option(wallet.getTransaction(input.getOutpoint.getHash))
      output <- Option(parentTx.getOutput(input.getOutpoint.getIndex.toInt))
    } yield output
    blockedOutputs.cancelUsage(releasedOutputs.toSet)
  }

  private def update(): Unit = synchronized {
    blockedOutputs.setSpendCandidates(wallet.calculateAllSpendCandidates(true).toSet)
    listeners.foreach { case (l, e) => e.execute(new Runnable {
      override def run() = l.onChange()
    })}
  }

  private def blockFunds(tx: MutableTransaction): Unit = {
    wallet.commitTx(tx)
  }

  private def blockFunds(to: Address, amount: BitcoinAmount): MutableTransaction = {
    val tx = wallet.createSend(to, amount.asSatoshi)
    blockFunds(tx)
    tx
  }

  private def blockMultisignFunds(requiredSignatures: Seq[PublicKey],
                          amount: BitcoinAmount,
                          fee: BitcoinAmount = Bitcoin.Zero): MutableTransaction =
    blockMultisignFunds(collectFunds(amount), requiredSignatures, amount, fee)

  private def blockMultisignFunds(inputs: Traversable[MutableTransactionOutput],
                          requiredSignatures: Seq[PublicKey],
                          amount: BitcoinAmount,
                          fee: BitcoinAmount): MutableTransaction = {
    require(amount.isPositive, s"Amount to block must be greater than zero ($amount given)")
    require(!fee.isNegative, s"Fee should be non-negative ($fee given)")
    val totalInputFunds = valueOf(inputs)
    require(totalInputFunds >= amount + fee,
      "Input funds must cover the amount of funds to commit and the TX fee")

    val tx = new MutableTransaction(wallet.getNetworkParameters)
    inputs.foreach(tx.addInput)
    tx.addMultisignOutput(amount, requiredSignatures)
    tx.addChangeOutput(totalInputFunds, amount + fee, wallet.getChangeAddress)
    tx.signInputs(SigHash.ALL, wallet)
    blockFunds(tx)
    tx
  }

  private def releaseFunds(tx: MutableTransaction): Unit = {
    val walletTx = getTransaction(tx.getHash).getOrElse(
      throw new IllegalArgumentException(s"${tx.getHashAsString} is not part of this wallet"))
    walletTx.getInputs.foreach { input =>
      val parentTx = input.getOutpoint.getConnectedOutput.getParentTransaction
      if (contains(parentTx)) {
        if (!input.disconnect()) {
          throw new IllegalStateException(s"cannot disconnect outputs from $input in $walletTx")
        }
        moveToPool(parentTx, WalletTransaction.Pool.UNSPENT)
      }
    }
    moveToPool(walletTx, WalletTransaction.Pool.DEAD)
  }

  private def collectFunds(amount: BitcoinAmount): Set[MutableTransactionOutput] = {
    val inputFundCandidates = wallet.calculateAllSpendCandidates(true)
    val necessaryInputCount =
      inputFundCandidates.view.scanLeft(Currency.Bitcoin.Zero)((accum, output) =>
        accum + Currency.Bitcoin.fromSatoshi(output.getValue))
        .takeWhile(_ < amount)
        .length
    inputFundCandidates.take(necessaryInputCount).toSet
  }

  private def contains(tx: MutableTransaction): Boolean = getTransaction(tx.getHash).isDefined

  private def getTransaction(txHash: Hash) = Option(wallet.getTransaction(txHash))

  private def valueOf(outputs: Traversable[MutableTransactionOutput]): BitcoinAmount =
    outputs.map(funds => Currency.Bitcoin.fromSatoshi(funds.getValue))
      .foldLeft(Bitcoin.Zero)(_ + _)

  private def moveToPool(tx: MutableTransaction, pool: WalletTransaction.Pool): Unit = {
    val wtxs = wallet.getWalletTransactions
    wallet.clearTransactions(0)
    wallet.addWalletTransaction(new WalletTransaction(pool, tx))
    wtxs.foreach { wtx =>
      if (tx.getHash != wtx.getTransaction.getHash) {
        wallet.addWalletTransaction(wtx)
      }
    }
  }

  wallet.addEventListener(new AbstractWalletEventListener {

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
      // Don't notify confidence changes for already confirmed transactions to reduce load
      if (tx.getConfidence.getConfidenceType != TransactionConfidence.ConfidenceType.BUILDING ||
        tx.getConfidence.getDepthInBlocks == 1) {
        onChange()
      }
    }

    override def onChange(): Unit = {
      update()
    }
  })
}

object SmartWallet {

  trait Listener {
    def onChange(): Unit
  }
}
