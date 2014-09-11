package coinffeine.peer.bitcoin

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

import akka.actor._
import com.google.bitcoin.core._

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin._
import coinffeine.model.currency.{BitcoinBalance, Balance, BitcoinAmount}
import coinffeine.model.event.WalletBalanceChangeEvent
import coinffeine.peer.CoinffeinePeerActor.{RetrieveWalletBalance, WalletBalance}
import coinffeine.peer.event.EventPublisher

private class WalletActor(properties: MutableWalletProperties, wallet: Wallet)
    extends Actor with ActorLogging with EventPublisher {

  import coinffeine.peer.bitcoin.WalletActor._

  private var lastBalanceReported: Option[BitcoinAmount] = None
  private val blockedOutputs = new BlockedOutputs()
  private var listeners = Set.empty[ActorRef]

  override def preStart(): Unit = {
    subscribeToWalletChanges()
    updateBalance()
    updateSpendCandidates()
    updateWalletPrimaryKeys()
  }

  override val receive: Receive = {

    case req @ CreateDeposit(coinsId, signatures, amount, transactionFee) =>
      try {
        val inputs = blockedOutputs.use(coinsId, amount + transactionFee)
        val tx = ImmutableTransaction(
          wallet.blockMultisignFunds(inputs, signatures, amount, transactionFee)
        )
        sender ! WalletActor.DepositCreated(req, tx)
      } catch {
        case NonFatal(ex) => sender ! WalletActor.DepositCreationError(req, ex)
      }

    case WalletActor.ReleaseDeposit(tx) =>
      wallet.releaseFunds(tx.get)
      val releasedOutputs = for {
        input <- tx.get.getInputs.asScala
        parentTx <- Option(wallet.getTransaction(input.getOutpoint.getHash))
        output <- Option(parentTx.getOutput(input.getOutpoint.getIndex.toInt))
      } yield output
      blockedOutputs.cancelUsage(releasedOutputs.toSet)

    case RetrieveWalletBalance =>
      sender() ! WalletBalance(wallet.balance())

    case CreateKeyPair =>
      val keyPair = new KeyPair()
      wallet.addKey(keyPair)
      sender() ! KeyPairCreated(keyPair)

    case InternalWalletChanged =>
      updateBalance()
      updateSpendCandidates()
      notifyListeners()

    case BlockBitcoins(amount) =>
      sender() ! blockedOutputs.block(amount)
        .fold[BlockBitcoinsResponse](CannotBlockBitcoins)(BlockedBitcoins.apply)

    case UnblockBitcoins(id) =>
      blockedOutputs.unblock(id)

    case SubscribeToWalletChanges =>
      context.watch(sender())
      listeners += sender()

    case UnsubscribeToWalletChanges =>
      context.unwatch(sender())
      listeners -= sender()

    case Terminated(listener) =>
      listeners -= listener
  }

  private def updateBalance(): Unit = {
    // TODO: do not use the event stream to inform of the balance
    val currentBalance = wallet.balance()
    if (lastBalanceReported != Some(currentBalance)) {
      publishEvent(WalletBalanceChangeEvent(Balance(currentBalance)))
      lastBalanceReported = Some(currentBalance)
    }

    properties.balance.set(Some(Balance(currentBalance)))
  }

  private def updateSpendCandidates(): Unit = {
    blockedOutputs.setSpendCandidates(wallet.calculateAllSpendCandidates(true).asScala.toSet)
  }

  private def updateWalletPrimaryKeys(): Unit = {
    val network = wallet.getNetworkParameters
    properties.primaryKeyPair.set(wallet.getKeys.headOption.map(_.toAddress(network)))
  }

  private def subscribeToWalletChanges(): Unit = {
    wallet.addEventListener(new AbstractWalletEventListener {
      override def onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction): Unit = {
        // Don't notify confidence changes for already confirmed transactions to reduce load
        if (tx.getConfidence.getConfidenceType != TransactionConfidence.ConfidenceType.BUILDING ||
          tx.getConfidence.getDepthInBlocks == 1) {
          onChange()
        }
      }

      override def onChange(): Unit = {
        self ! InternalWalletChanged
      }
    })
  }

  private def notifyListeners(): Unit = {
    listeners.foreach(_ ! WalletChanged)
  }
}

object WalletActor {
  private[bitcoin] def props(properties: MutableWalletProperties, wallet: Wallet) =
    Props(new WalletActor(properties, wallet))

  private case object InternalWalletChanged

  /** Subscribe to wallet changes. The sender will receive [[WalletChanged]] after sending this
    * message to the wallet actor and until being stopped or sending [[UnsubscribeToWalletChanges]].
    */
  case object SubscribeToWalletChanges
  case object UnsubscribeToWalletChanges
  case object WalletChanged

  /** A message sent to the wallet actor to block an amount of coins for exclusive use. */
  case class BlockBitcoins(amount: BitcoinAmount)

  /** Responses to [[BlockBitcoins]] */
  sealed trait BlockBitcoinsResponse

  /** Bitcoin amount was blocked successfully */
  case class BlockedBitcoins(id: BlockedCoinsId) extends BlockBitcoinsResponse

  /** Cannot block the requested amount of bitcoins */
  case object CannotBlockBitcoins extends BlockBitcoinsResponse

  /** A message sent to the wallet actor to release for general use the previously blocked
    * bitcoins.
    */
  case class UnblockBitcoins(id: BlockedCoinsId)

  /** A message sent to the wallet actor in order to create a multisigned deposit transaction.
    *
    * This message requests some funds to be mark as used by the wallet. This will produce a new
    * transaction included in a [[DepositCreated]] reply message, or [[DepositCreationError]] if
    * something goes wrong. The resulting transaction can be safely sent to the blockchain with the
    * guarantee that the outputs it spends are not used in any other transaction. If the transaction
    * is not finally broadcast to the blockchain, the funds can be unblocked by sending a
    * [[ReleaseDeposit]] message.
    *
    * @param coinsId            Source of the bitcoins to use for this deposit
    * @param requiredSignatures The signatures required to spend the tx in a multisign script
    * @param amount             The amount of bitcoins to be blocked and included in the transaction
    * @param transactionFee     The fee to include in the transaction
    */
  case class CreateDeposit(coinsId: BlockedCoinsId,
                           requiredSignatures: Seq[KeyPair],
                           amount: BitcoinAmount,
                           transactionFee: BitcoinAmount)

  /** A message sent by the wallet actor in reply to a [[CreateDeposit]] message to report
    * a successful funds blocking.
    *
    * @param request  The request this message is replying to
    * @param tx       The resulting transaction that contains the funds that have been blocked
    */
  case class DepositCreated(request: CreateDeposit, tx: ImmutableTransaction)

  /** A message sent by the wallet actor in reply to a [[CreateDeposit]] message to report
    * an error while blocking the funds.
    */
  case class DepositCreationError(request: CreateDeposit, error: Throwable)

  /** A message sent to the wallet actor in order to release the funds that of a non published
    * deposit.
    */
  case class ReleaseDeposit(tx: ImmutableTransaction)

  /** A message sent to the wallet actor to ask for a fresh key pair */
  case object CreateKeyPair

  /** Response to [[CreateKeyPair]] */
  case class KeyPairCreated(keyPair: KeyPair)
}
