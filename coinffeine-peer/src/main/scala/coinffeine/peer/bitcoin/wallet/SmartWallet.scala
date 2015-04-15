package coinffeine.peer.bitcoin.wallet

import java.io.{File, FileInputStream, InputStream}
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext

import org.bitcoinj.core.Wallet.{BalanceType, SendRequest}
import org.bitcoinj.core.{Wallet => _, _}
import org.bitcoinj.wallet.WalletTransaction

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency._

class SmartWallet(val delegate: Wallet) {

  import SmartWallet._

  type Inputs = Set[TransactionOutPoint]

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

  def findTransactionSpendingOutput(outPoint: TransactionOutPoint): Option[ImmutableTransaction] =
    for {
      tx <- Option(delegate.getTransaction(outPoint.getHash))
      output <- Option(tx.getOutput(outPoint.getIndex.toInt))
      input <- Option(output.getSpentBy)
    } yield ImmutableTransaction(input.getParentTransaction)

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
    request.coinSelector = new HandpickedCoinSelector(inputs)
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

  /** Mark the inputs of the given transaction as unspent */
  def releaseTransaction(tx: ImmutableTransaction): Unit = synchronized {
    val walletTx = getTransaction(tx.get.getHash).getOrElse(
      throw new IllegalArgumentException(s"${tx.get.getHashAsString} is not part of this wallet"))
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
    update()
  }

  def createMultisignTransaction(requiredSignatures: Both[PublicKey],
                                 amount: Bitcoin.Amount,
                                 fee: Bitcoin.Amount = Bitcoin.Zero): ImmutableTransaction =
    createMultisignTransaction(collectFunds(amount), requiredSignatures, amount, fee)

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
    inputs.foreach { outPoint =>
      tx.addInput(outPoint.getConnectedOutput)
    }
    tx.addMultisigOutput(amount, requiredSignatures.toSeq)
    tx.addChangeOutput(totalInputFunds, amount + fee, delegate.getChangeAddress)

    delegate.signTransaction(SendRequest.forTx(tx))
    commitTransaction(tx)
    ImmutableTransaction(tx)
  }

  def spendCandidates: Seq[MutableTransactionOutput] = synchronized {
    delegate.calculateAllSpendCandidates(ExcludeImmatureCoinBases)
      .filter(!_.getParentTransaction.isPending)
  }

  private def update(): Unit = synchronized {
    listeners.values.foreach(_.apply())
  }

  private def collectFunds(amount: Bitcoin.Amount): Inputs = {
    val inputFundCandidates = spendCandidates
    val necessaryInputCount =
      inputFundCandidates.view.scanLeft(Bitcoin.Zero)(_ + _.getValue)
        .takeWhile(_ < amount)
        .length
    inputFundCandidates.take(necessaryInputCount).map(_.getOutPointFor).toSet
  }

  private def commitTransaction(tx: MutableTransaction): Unit = {
    def containsTransaction(pool: WalletTransaction.Pool) =
      delegate.getTransactionPool(pool).contains(tx.getHash)
    if (!WalletTransaction.Pool.values().exists(containsTransaction)) {
      delegate.commitTx(tx)
    }
  }

  private def contains(tx: MutableTransaction): Boolean = getTransaction(tx.getHash).isDefined

  private def getTransaction(txHash: Hash) = Option(delegate.getTransaction(txHash))

  private def valueOf(inputs: Inputs): Bitcoin.Amount = inputs.toSeq.map(valueOf).sum

  private def valueOf(input: TransactionOutPoint): Bitcoin.Amount =
    Option(input.getConnectedOutput)
      .getOrElse(delegate.getTransaction(input.getHash).getOutput(input.getIndex.toInt))
      .getValue

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
    try {
      loadFromStream(stream)
    } finally {
      stream.close()
    }
  }

  def loadFromStream(stream: InputStream): SmartWallet = {
    new SmartWallet(Wallet.loadFromFileStream(stream))
  }

  case class NotEnoughFunds(message: String, cause: Throwable = null)
    extends RuntimeException(message, cause)
}
