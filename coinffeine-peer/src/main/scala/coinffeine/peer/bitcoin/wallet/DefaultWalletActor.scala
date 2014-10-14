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

  override def receiveRecover: Receive = {
    case event: FundsBlocked => onFundsBlocked(event)
    case event: FundsUnblocked => onFundsUnblocked(event)
    case event: DepositCreated => onDepositCreated(event)
    case event: DepositCancelled => onDepositCancelled(event)
  }

  override def receiveCommand: Receive = {

    case request @ CreateDeposit(coinsId, signatures, amount, transactionFee) =>
      blockedOutputs.canUse(coinsId, amount + transactionFee) match {
        case scalaz.Success(outputs) =>
          persist(DepositCreated(request, outputs)) { event =>
            val tx = onDepositCreated(event)
            sender ! WalletActor.DepositCreated(request, tx)
          }
        case scalaz.Failure(error) =>
          sender() ! WalletActor.DepositCreationError(request, error)
      }

    case req @ WalletActor.CreateTransaction(amount, to) =>
      try {
        val funds = blockedOutputs.collectUnblockedFunds(amount)
          .getOrElse(throw new Exception("Not enough funds"))
        val tx = wallet.createTransaction(funds, amount, to)
        sender ! WalletActor.TransactionCreated(req, tx)
      } catch {
        case NonFatal(ex) => sender ! WalletActor.TransactionCreationFailure(req, ex)
      }

    case WalletActor.ReleaseDeposit(tx) =>
      persist(DepositCancelled(tx))(onDepositCancelled)

    case CreateKeyPair =>
      sender() ! KeyPairCreated(wallet.freshKeyPair())

    case InternalWalletChanged =>
      updateBalance()
      updateActivity()
      notifyListeners()

    case BlockBitcoins(fundsId, _) if blockedOutputs.areBlocked(fundsId) =>
      sender() ! CannotBlockBitcoins

    case BlockBitcoins(fundsId, amount) =>
      blockedOutputs.collectFunds(amount) match {
        case Some(funds) =>
          persist(FundsBlocked(fundsId, funds)) { event =>
            onFundsBlocked(event)
            sender() ! BlockedBitcoins(fundsId)
          }
        case None =>
          sender() ! CannotBlockBitcoins
      }

    case UnblockBitcoins(fundsId) if blockedOutputs.areBlocked(fundsId) =>
      persist(FundsUnblocked(fundsId))(onFundsUnblocked)

    case SubscribeToWalletChanges =>
      context.watch(sender())
      listeners += sender()

    case UnsubscribeToWalletChanges =>
      context.unwatch(sender())
      listeners -= sender()

    case Terminated(listener) =>
      listeners -= listener
  }

  private def onFundsBlocked(event: FundsBlocked): Unit = {
    blockedOutputs.block(event.id, event.outputs)
  }

  private def onFundsUnblocked(event: FundsUnblocked): Unit = {
    blockedOutputs.unblock(event.id)
  }

  private def onDepositCreated(event: DepositCreated): ImmutableTransaction = {
    import event.request._
    blockedOutputs.use(coinsId, event.outputs)
    wallet.createMultisignTransaction(event.outputs, requiredSignatures, amount, transactionFee)
  }

  private def onDepositCancelled(event: DepositCancelled): Unit = {
    wallet.releaseTransaction(event.tx)
    val releasedOutputs = event.tx.get.getInputs.map(_.getOutpoint).toSet
    blockedOutputs.cancelUsage(releasedOutputs)
  }

  private def updateActivity(): Unit = {
    val transactions = wallet.delegate.getTransactionsByTime
    properties.activity.set(WalletActivity(wallet.delegate, transactions: _*))
  }

  private def updateBalance(): Unit = {
    blockedOutputs.setSpendCandidates(wallet.spendCandidates.map(_.getOutPointFor).toSet)
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

  private case class FundsBlocked(id: ExchangeId, outputs: Set[TransactionOutPoint])
  private case class FundsUnblocked(id: ExchangeId)
  private case class DepositCreated(request: CreateDeposit, outputs: Set[TransactionOutPoint])
  private case class DepositCancelled(tx: ImmutableTransaction)
}
