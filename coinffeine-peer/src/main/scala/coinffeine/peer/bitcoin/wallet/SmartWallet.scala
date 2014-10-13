package coinffeine.peer.bitcoin.wallet

import java.io.{File, FileInputStream}
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import org.bitcoinj.core.Wallet.{BalanceType, SendRequest}
import org.bitcoinj.core._
import org.bitcoinj.wallet.WalletTransaction

import coinffeine.model.bitcoin.{Address, Wallet, _}
import coinffeine.model.currency._
import coinffeine.model.exchange.Both

class SmartWallet(val delegate: Wallet) {

  import SmartWallet._

  type Inputs = Traversable[MutableTransactionOutput]

  def this(network: Network) = this(new Wallet(network))

  private case class ListenerExecutor(listener: Listener, context: ExecutionContext) {
    private val task = new Runnable {
      override def run(): Unit = {
        listener.onChange()
      }
    }
    def apply(): Unit = {
      context.execute(task)
    }
  }
  private var listeners: Map[Listener, ListenerExecutor] = Map.empty

  def addListener(listener: Listener)(implicit executor: ExecutionContext) = synchronized {
    listeners += listener -> ListenerExecutor(listener, executor)
  }

  def removeListener(listener: Listener) = synchronized {
    listeners -= listener
  }

  def currentReceiveAddress: Address = synchronized {
    delegate.currentReceiveAddress()
  }

  def estimatedBalance: Bitcoin.Amount = synchronized {
    delegate.getBalance(BalanceType.ESTIMATED)
  }

  def availableBalance: Bitcoin.Amount = synchronized {
    delegate.getBalance(BalanceType.AVAILABLE)
  }

  def value(tx: MutableTransaction): Bitcoin.Amount = tx.getValue(delegate)

  def valueSentFromMe(tx: MutableTransaction): Bitcoin.Amount = tx.getValueSentFromMe(delegate)

  def valueSentToMe(tx: MutableTransaction): Bitcoin.Amount = tx.getValueSentToMe(delegate)

  def freshKeyPair(): KeyPair = synchronized {
    delegate.freshReceiveKey()
  }

  def createTransaction(inputs: Inputs,
                        amount: Bitcoin.Amount,
                        to: Address): ImmutableTransaction = synchronized {
    val request = SendRequest.to(to, amount)
    request.coinSelector = new HandpickedCoinSelector(inputs.toSet)
    createTransaction(request)
  }

  def createTransaction(amount: Bitcoin.Amount, to: Address): ImmutableTransaction = synchronized {
    createTransaction(SendRequest.to(to, amount))
  }

  private def createTransaction(request: SendRequest): ImmutableTransaction = {
    val result = try {
      delegate.sendCoinsOffline(request)
    } catch {
      case ex: InsufficientMoneyException =>
        throw new NotEnoughFunds("Cannot create transaction", ex)
    }
    ImmutableTransaction(result.tx)
  }

  /** Mark the inputs of the given transaction as unspent. Returns the affected outputs. */
  def releaseTransaction(tx: ImmutableTransaction): Set[MutableTransactionOutput] = synchronized {
    releaseFunds(tx.get)
    (for {
      input <- tx.get.getInputs.toList
      parentTx <- Option(delegate.getTransaction(input.getOutpoint.getHash))
      output <- Option(parentTx.getOutput(input.getOutpoint.getIndex.toInt))
    } yield output).toSet
  }

  def createMultisignTransaction(requiredSignatures: Both[PublicKey],
                                 amount: Bitcoin.Amount,
                                 fee: Bitcoin.Amount = Bitcoin.Zero): ImmutableTransaction = try {
    createMultisignTransaction(collectFunds(amount), requiredSignatures, amount, fee)
  } catch {
    case e: BlockedOutputs.NotEnoughFunds => throw new NotEnoughFunds(
      "cannot block multisign funds: not enough funds", e)
  }

  def createMultisignTransaction(inputs: Inputs,
                                 requiredSignatures: Both[PublicKey],
                                 amount: Bitcoin.Amount,
                                 fee: Bitcoin.Amount): ImmutableTransaction = synchronized {
    require(amount.isPositive, s"Amount to block must be greater than zero ($amount given)")
    require(!fee.isNegative, s"Fee should be non-negative ($fee given)")
    val totalInputFunds = valueOf(inputs)
    if (totalInputFunds < amount + fee) {
      throw new NotEnoughFunds(
        s"""Not enough funds: $totalInputFunds is not enough for
           |putting $amount in multisig with a fee of $fee""".stripMargin)
    }

    val tx = new MutableTransaction(delegate.getNetworkParameters)
    inputs.foreach(tx.addInput)
    tx.addMultisignOutput(amount, requiredSignatures.toSeq)
    tx.addChangeOutput(totalInputFunds, amount + fee, delegate.getChangeAddress)

    delegate.signTransaction(SendRequest.forTx(tx))
    delegate.commitTx(tx)
    ImmutableTransaction(tx)
  }

  def spendCandidates: Seq[MutableTransactionOutput] = synchronized {
    delegate.calculateAllSpendCandidates(ExcludeImmatureCoinBases)
  }

  private def update(): Unit = synchronized {
    listeners.values.foreach(_.apply())
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

  private def collectFunds(amount: Bitcoin.Amount): Set[MutableTransactionOutput] = {
    val inputFundCandidates = spendCandidates
    val necessaryInputCount =
      inputFundCandidates.view.scanLeft(Bitcoin.Zero)(_ + _.getValue)
        .takeWhile(_ < amount)
        .length
    inputFundCandidates.take(necessaryInputCount).toSet
  }

  private def contains(tx: MutableTransaction): Boolean = getTransaction(tx.getHash).isDefined

  private def getTransaction(txHash: Hash) = Option(delegate.getTransaction(txHash))

  private def valueOf(inputs: Inputs): Bitcoin.Amount =
    inputs.map(funds => Bitcoin.fromSatoshi(funds.getValue.value)).sum

  private def moveToPool(tx: MutableTransaction, pool: WalletTransaction.Pool): Unit = {
    val wtxs = delegate.getWalletTransactions
    delegate.clearTransactions(0)
    delegate.addWalletTransaction(new WalletTransaction(pool, tx))
    wtxs.foreach { wtx =>
      if (tx.getHash != wtx.getTransaction.getHash) {
        delegate.addWalletTransaction(wtx)
      }
    }
  }

  delegate.addEventListener(new AbstractWalletEventListener {

    override def onTransactionConfidenceChanged(wallet: Wallet, tx: MutableTransaction): Unit = {
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

  private val ExcludeImmatureCoinBases = true

  trait Listener {
    def onChange(): Unit
  }

  def loadFromFile(file: File): SmartWallet = {
    val stream = new FileInputStream(file)
    val delegate = try {
      Wallet.loadFromFileStream(stream)
    } finally {
      stream.close()
    }
    new SmartWallet(delegate)
  }

  case class NotEnoughFunds(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)
}
