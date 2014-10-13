package coinffeine.peer.bitcoin.wallet

import scala.collection.JavaConversions._
import scala.util.control.NonFatal

import akka.actor._
import akka.persistence.PersistentActor
import org.bitcoinj.core.TransactionOutPoint

import coinffeine.model.bitcoin.{ImmutableTransaction, WalletActivity, MutableWalletProperties}
import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.WalletActor._

private class DefaultWalletActor(properties: MutableWalletProperties,
                                 wallet: SmartWallet,
                                 override val persistenceId: String)
  extends PersistentActor with ActorLogging {

  import DefaultWalletActor._

  private val blockedOutputs = new BlockedOutputs()
  private var listeners = Set.empty[ActorRef]

  override def preStart(): Unit = {
    subscribeToWalletChanges()
    updateBalance()
    updateWalletPrimaryKeys()
    updateActivity()
    super.preStart()
  }

  override def postStop(): Unit = {
    super.postStop()
    unsubscribeFromWalletChanges()
  }

  override def receiveRecover: Receive = Map.empty

  override def receiveCommand: Receive = {

    case req @ CreateDeposit(coinsId, signatures, amount, transactionFee) =>
      try {
        val inputs = blockedOutputs.use(coinsId, amount + transactionFee)
        val tx = wallet.createMultisignTransaction(inputs, signatures, amount, transactionFee)
        sender ! WalletActor.DepositCreated(req, tx)
      } catch {
        case NonFatal(ex) => sender ! WalletActor.DepositCreationError(req, ex)
      }

    case req @ WalletActor.CreateTransaction(amount, to) =>
      try {
        val tx = wallet.createTransaction(blockedOutputs.useUnblockedFunds(amount), amount, to)
        sender ! WalletActor.TransactionCreated(req, tx)
      } catch {
        case NonFatal(ex) => sender ! WalletActor.TransactionCreationFailure(req, ex)
      }

    case WalletActor.ReleaseDeposit(tx) =>
      val releasedOutputs = wallet.releaseTransaction(tx)
      blockedOutputs.cancelUsage(releasedOutputs)

    case CreateKeyPair =>
      sender() ! KeyPairCreated(wallet.freshKeyPair())

    case InternalWalletChanged =>
      updateBalance()
      updateActivity()
      notifyListeners()

    case BlockBitcoins(fundsId, amount) =>
      sender() ! blockedOutputs.block(fundsId, amount)
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

  private def updateActivity(): Unit = {
    val transactions = wallet.delegate.getTransactionsByTime
    properties.activity.set(WalletActivity(wallet.delegate, transactions: _*))
  }

  private def updateBalance(): Unit = {
    blockedOutputs.setSpendCandidates(wallet.spendCandidates.toSet)
    properties.balance.set(Some(BitcoinBalance(
      estimated = wallet.estimatedBalance,
      available = wallet.availableBalance,
      minOutput = blockedOutputs.minOutput,
      blocked = blockedOutputs.blocked
    )))
  }

  private def updateWalletPrimaryKeys(): Unit = {
    properties.primaryAddress.set(Some(wallet.currentReceiveAddress))
  }

  private def subscribeToWalletChanges(): Unit = {
    wallet.addListener(WalletListener)(context.dispatcher)
  }

  private def unsubscribeFromWalletChanges(): Unit = {
    wallet.removeListener(WalletListener)
  }

  private def notifyListeners(): Unit = {
    listeners.foreach(_ ! WalletChanged)
  }

  private object WalletListener extends SmartWallet.Listener {
    override def onChange() = {
      self ! InternalWalletChanged
    }
  }
}

object DefaultWalletActor {
  val PersistenceId = "wallet"

  def props(properties: MutableWalletProperties,
            wallet: SmartWallet,
            persistenceId: String = PersistenceId) =
    Props(new DefaultWalletActor(properties, wallet, persistenceId))

  private case object InternalWalletChanged

  private case class FundsBlocked(id: ExchangeId, outputs: Seq[TransactionOutPoint])
  private case class FundsUnblocked(id: ExchangeId)
  private case class DepositCreated(tx: ImmutableTransaction)
  private case class DepositCancelled(tx: ImmutableTransaction)
}
