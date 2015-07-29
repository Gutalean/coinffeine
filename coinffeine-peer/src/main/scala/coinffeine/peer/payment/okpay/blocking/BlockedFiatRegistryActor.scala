package coinffeine.peer.payment.okpay.blocking

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.joda.time.DateTime

import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.currency.{FiatAmount, FiatAmounts}
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor

private[okpay] class BlockedFiatRegistryActor(override val persistenceId: String)
    extends PersistentActor with PeriodicSnapshot with ActorLogging {

  import BlockedFiatRegistryActor._

  private val registry = new BlockedFiatRegistryImpl

  override def receiveRecover: Receive = {
    case event: FundsBlockedEvent => onFundsBlocked(event)
    case event: FundsMarkedUsedEvent => onFundsMarkedUsed(event)
    case event: FundsUnmarkedUsedEvent => onFundsUnmarkedUsed(event)
    case event: FundsUnblockedEvent => onFundsUnblocked(event)
    case SnapshotOffer(metadata, snapshot: Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      restoreSnapshot(snapshot)
    case RecoveryCompleted =>
      registry.notifyAvailabilityChanges(AvailabilityNotifier)
  }

  override protected def createSnapshot: Option[PersistentEvent] =
    Some(Snapshot(registry.takeMemento))

  private def restoreSnapshot(snapshot: Snapshot): Unit = {
    registry.restoreMemento(snapshot.funds)
    updateBackedFunds()
  }

  override def receiveCommand: Receive = managingSnapshots orElse {
    case RetrieveTotalBlockedFunds =>
      sender() ! BlockedFiatRegistryActor.TotalBlockedFunds(registry.blockedFundsByCurrency)

    case AccountUpdate(balances, remainingLimits) =>
      registry.updateTransientAmounts(balances, remainingLimits)
      updateBackedFunds()

    case MarkUsed(fundsId, amount) =>
      registry.canMarkUsed(fundsId, amount).fold(
        succ = funds => persist(FundsMarkedUsedEvent(fundsId, amount)) { event =>
          onFundsMarkedUsed(event)
          sender() ! FundsMarkedUsed(fundsId, amount)
        },
        fail = reason => sender() ! CannotMarkUsed(fundsId, amount, reason)
      )

    case UnmarkUsed(fundsId, amount) =>
      registry.canUnmarkUsed(fundsId, amount).fold(
        succ = _ => persist(FundsUnmarkedUsedEvent(fundsId, amount))(onFundsUnmarkedUsed),
        fail = reason => log.warning("cannot unmark funds {}: {}", fundsId, reason)
      )

    case PaymentProcessorActor.BlockFunds(fundsId, _) if registry.contains(fundsId) =>
      sender() ! PaymentProcessorActor.AlreadyBlockedFunds(fundsId)

    case PaymentProcessorActor.BlockFunds(fundsId, amount) =>
      persist(FundsBlockedEvent(fundsId, amount)) { event =>
        sender() ! PaymentProcessorActor.BlockedFunds(fundsId)
        onFundsBlocked(event)
      }

    case PaymentProcessorActor.UnblockFunds(fundsId) =>
      persist(FundsUnblockedEvent(fundsId))(onFundsUnblocked)
  }

  private def onFundsBlocked(event: FundsBlockedEvent): Unit = {
    registry.block(event.fundsId, event.amount)
    updateBackedFunds()
  }

  private def onFundsUnblocked(event: FundsUnblockedEvent): Unit = {
    registry.unblock(event.fundsId)
    updateBackedFunds()
  }

  private def onFundsMarkedUsed(event: FundsMarkedUsedEvent): Unit = {
    registry.markUsed(event.fundsId, event.amount)
    updateBackedFunds()
  }

  private def onFundsUnmarkedUsed(event: FundsUnmarkedUsedEvent): Unit = {
    registry.unmarkUsed(event.fundsId, event.amount)
    updateBackedFunds()
  }

  private def updateBackedFunds(): Unit = {
    if (recoveryFinished) {
      registry.notifyAvailabilityChanges(AvailabilityNotifier)
    }
  }

  private object AvailabilityNotifier extends AvailabilityListener {
    override def onAvailable(funds: ExchangeId): Unit = {
      log.debug("{} fiat funds becomes available", funds)
      context.system.eventStream.publish(PaymentProcessorActor.AvailableFunds(funds))
    }
    override def onUnavailable(funds: ExchangeId): Unit = {
      log.debug("{} fiat funds becomes unavailable", funds)
      context.system.eventStream.publish(PaymentProcessorActor.UnavailableFunds(funds))
    }
  }
}

private[okpay] object BlockedFiatRegistryActor {

  val PersistenceId = "blockedFiatRegistry"

  case object RetrieveTotalBlockedFunds

  case class TotalBlockedFunds(funds: FiatAmounts)

  case class AccountUpdate(balances: FiatAmounts, remainingLimits: FiatAmounts)

  /** Request funds for immediate use.
    *
    * To be replied with either [[FundsMarkedUsed]] or [[CannotMarkUsed]]
    */
  case class MarkUsed(funds: ExchangeId, amount: FiatAmount)

  case class FundsMarkedUsed(funds: ExchangeId, amount: FiatAmount)

  case class CannotMarkUsed(funds: ExchangeId, amount: FiatAmount, reason: String)

  /** Request funds to be unmarked. Don't expect any reply */
  case class UnmarkUsed(funds: ExchangeId, amount: FiatAmount)

  private sealed trait StateEvent extends PersistentEvent

  private case class FundsBlockedEvent(
      fundsId: ExchangeId, amount: FiatAmount) extends StateEvent

  private case class FundsMarkedUsedEvent(
      fundsId: ExchangeId, amount: FiatAmount) extends StateEvent

  private case class FundsUnmarkedUsedEvent(
      fundsId: ExchangeId, amount: FiatAmount) extends StateEvent

  private case class FundsUnblockedEvent(fundsId: ExchangeId) extends StateEvent

  case class BlockedFundsInfo(id: ExchangeId, remainingAmount: FiatAmount) {
    val timestamp = DateTime.now()
    def canUseFunds(amount: FiatAmount): Boolean = amount <= remainingAmount
  }

  private case class Snapshot(funds: Map[ExchangeId, BlockedFundsInfo])
      extends PersistentEvent

  def props = Props(new BlockedFiatRegistryActor(PersistenceId))
}
