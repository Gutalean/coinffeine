package coinffeine.peer.payment.okpay.blocking

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._
import scalaz.syntax.validation._

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.joda.time.DateTime

import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.currency.{FiatAmount, FiatAmounts, FiatCurrency}
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.PaymentProcessorActor

private[okpay] class BlockedFiatRegistry(override val persistenceId: String)
    extends PersistentActor with PeriodicSnapshot with ActorLogging {

  import BlockedFiatRegistry._

  private var balances = FiatAmounts.empty
  private var remainingLimits = FiatAmounts.empty
  private var funds: Map[ExchangeId, BlockedFundsInfo] = Map.empty
  private val fundsAvailability = new BlockedFundsAvailability()

  override def receiveRecover: Receive = {
    case event: FundsBlockedEvent => onFundsBlocked(event)
    case event: FundsMarkedUsedEvent => onFundsMarkedUsed(event)
    case event: FundsUnmarkedUsedEvent => onFundsUnmarkedUsed(event)
    case event: FundsUnblockedEvent => onFundsUnblocked(event)
    case SnapshotOffer(metadata, snapshot: Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      restoreSnapshot(snapshot)
    case RecoveryCompleted => notifyAvailabilityChanges()
  }

  override protected def createSnapshot: Option[PersistentEvent] = Some(Snapshot(funds))

  private def restoreSnapshot(snapshot: Snapshot): Unit = {
    funds = snapshot.funds
    funds.keys.foreach(fundsAvailability.addFunds)
    updateBackedFunds()
  }

  override def receiveCommand: Receive = managingSnapshots orElse {
    case RetrieveTotalBlockedFunds(currency) =>
      totalBlockedForCurrency(currency) match {
        case Some(blockedFunds) => sender ! BlockedFiatRegistry.TotalBlockedFunds(blockedFunds)
        case None => sender ! BlockedFiatRegistry.TotalBlockedFunds(currency.zero)
      }

    case AccountUpdate(newBalances, newRemainingLimits) =>
      balances = newBalances
      remainingLimits = newRemainingLimits
      updateBackedFunds()

    case MarkUsed(fundsId, amount) =>
      canMarkUsed(fundsId, amount).fold(
        succ = funds => persist(FundsMarkedUsedEvent(fundsId, amount)) { event =>
          onFundsMarkedUsed(event)
          sender() ! FundsMarkedUsed(fundsId, amount)
        },
        fail = reason => sender() ! CannotMarkUsed(fundsId, amount, reason)
      )

    case UnmarkUsed(fundsId, amount) =>
      canUnmarkUsed(fundsId, amount).fold(
        succ = funds => persist(FundsUnmarkedUsedEvent(fundsId, amount))(onFundsUnmarkedUsed),
        fail = reason => log.warning("cannot unmark funds {}: {}", fundsId, reason)
      )

    case PaymentProcessorActor.BlockFunds(fundsId, _) if funds.contains(fundsId) =>
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
    funds += event.fundsId -> BlockedFundsInfo(event.fundsId, event.amount)
    fundsAvailability.addFunds(event.fundsId)
    updateBackedFunds()
  }

  private def onFundsMarkedUsed(event: FundsMarkedUsedEvent): Unit = {
    val fundsToUse = funds(event.fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount - event.amount))
    balances = balances.decrement(event.amount)
    updateBackedFunds()
  }

  private def onFundsUnmarkedUsed(event: FundsUnmarkedUsedEvent): Unit = {
    val fundsToUse = funds(event.fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount + event.amount))
    balances = balances.increment(event.amount)
    updateBackedFunds()
  }

  private def onFundsUnblocked(event: FundsUnblockedEvent): Unit = {
    funds -= event.fundsId
    fundsAvailability.removeFunds(event.fundsId)
    updateBackedFunds()
  }

  private def canMarkUsed(
      fundsId: ExchangeId, amount: FiatAmount): Validation[String, BlockedFundsInfo] = for {
    funds <- requireExistingFunds(fundsId, amount.currency)
    _ <- requireEnoughBalance(funds, amount)
    _ <- requiredBackedFunds(funds.id)
  } yield funds

  private def canUnmarkUsed(
      fundsId: ExchangeId, amount: FiatAmount): Validation[String, BlockedFundsInfo] = for {
    funds <- requireExistingFunds(fundsId, amount.currency)
  } yield funds

  private def requireExistingFunds(
      fundsId: ExchangeId, currency: FiatCurrency): Validation[String, BlockedFundsInfo] =
    funds.get(fundsId).toSuccess(s"no such funds with id $fundsId")
        .ensure(s"cannot spend $currency out of $fundsId")(_.remainingAmount.currency == currency)

  private def requireEnoughBalance(
      funds: BlockedFundsInfo, minimumBalance: FiatAmount): Validation[String, Unit] =
    if (funds.remainingAmount >= minimumBalance) ().success
    else s"""insufficient blocked funds for id ${funds.id}: $minimumBalance requested,
            |${funds.remainingAmount} available""".stripMargin.failure

  private def requiredBackedFunds(fundsId: ExchangeId): Validation[String, Unit] =
    if (fundsAvailability.areAvailable(fundsId)) ().success
    else s"funds with id $fundsId are not currently available".failure

  private def updateFunds(newFunds: BlockedFundsInfo): Unit = {
    funds += newFunds.id -> newFunds
  }

  private def updateBackedFunds(): Unit = {
    fundsAvailability.clearAvailable()
    for (currency <- currenciesInUse();
         funds <- fundsThatCanBeBacked(currency)) {
      fundsAvailability.setAvailable(funds)
    }
    if (recoveryFinished) {
      notifyAvailabilityChanges()
    }
  }

  private def fundsThatCanBeBacked(currency: FiatCurrency): Set[ExchangeId] = {
    val balance = transferableBalance(currency)
    val eligibleFunds = funds
        .values
        .filter(_.remainingAmount.currency == currency)
        .toSeq
        .sortBy(_.timestamp.getMillis)
    val fundsThatCanBeBacked =
      eligibleFunds.scanLeft(currency.zero)(_ + _.remainingAmount)
          .takeWhile(_ <= balance)
          .size - 1
    eligibleFunds.take(fundsThatCanBeBacked).map(_.id).toSet
  }

  private def transferableBalance(currency: FiatCurrency): FiatAmount = {
    val availableBalance = balances.get(currency).getOrElse(currency.zero)
    val remainingLimit = remainingLimits.get(currency)
    remainingLimit.fold(availableBalance)(_ min availableBalance)
  }

  private def notifyAvailabilityChanges(): Unit = {
    fundsAvailability.notifyChanges(
      onAvailable = funds => {
        log.debug("{} fiat funds becomes available", funds)
        context.system.eventStream.publish(PaymentProcessorActor.AvailableFunds(funds))
      },
      onUnavailable = funds => {
        log.debug("{} fiat funds becomes unavailable", funds)
        context.system.eventStream.publish(PaymentProcessorActor.UnavailableFunds(funds))
      }
    )
  }

  private def currenciesInUse(): Set[FiatCurrency] =
    funds.values.map(_.remainingAmount.currency).toSet

  private def totalBlockedForCurrency(currency: FiatCurrency): Option[FiatAmount] = {
    val fundsForCurrency = funds.values
        .filter(_.remainingAmount.currency == currency)
        .asInstanceOf[Iterable[BlockedFundsInfo]]
    if (fundsForCurrency.isEmpty) None
    else Some(fundsForCurrency.map(_.remainingAmount).reduce(_ + _))
  }
}

private[okpay] object BlockedFiatRegistry {

  val PersistenceId = "blockedFiatRegistry"

  case class RetrieveTotalBlockedFunds(currency: FiatCurrency)

  case class TotalBlockedFunds(funds: FiatAmount)

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

  private case class BlockedFundsInfo(
      id: ExchangeId, remainingAmount: FiatAmount) {
    val timestamp = DateTime.now()

    def canUseFunds(amount: FiatAmount): Boolean = amount <= remainingAmount
  }

  private case class Snapshot(funds: Map[ExchangeId, BlockedFundsInfo])
      extends PersistentEvent

  def props = Props(new BlockedFiatRegistry(PersistenceId))
}
