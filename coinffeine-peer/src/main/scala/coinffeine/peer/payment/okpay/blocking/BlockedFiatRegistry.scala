package coinffeine.peer.payment.okpay.blocking

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._
import scalaz.syntax.validation._

import org.joda.time.DateTime

import coinffeine.common.akka.persistence.PersistentEvent
import coinffeine.model.currency.{FiatAmounts, FiatAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId

private[okpay] trait BlockedFiatRegistry {

  import BlockedFiatRegistry._

  def updateTransientAmounts(newBalances: FiatAmounts, newRemainingLimits: FiatAmounts): Unit

  def takeMemento: Map[ExchangeId, BlockedFundsInfo]
  def restoreMemento(snapshot: Map[ExchangeId, BlockedFundsInfo]): Unit

  def contains(fundsId: ExchangeId): Boolean
  def blockedFundsByCurrency: FiatAmounts

  def notifyAvailabilityChanges(listener: AvailabilityListener): Unit

  def block(fundsId: ExchangeId, amount: FiatAmount): Unit
  def unblock(fundsId: ExchangeId): Unit

  def canMarkUsed(fundsId: ExchangeId, amount: FiatAmount): Validation[String, BlockedFundsInfo]
  def markUsed(fundsId: ExchangeId, amount: FiatAmount): Unit

  def canUnmarkUsed(fundsId: ExchangeId, amount: FiatAmount): Validation[String, Unit]
  def unmarkUsed(fundsId: ExchangeId, amount: FiatAmount): Unit
}

private[okpay] object BlockedFiatRegistry {

  // Persistent messages stuck here because of binary compatibility
  sealed trait StateEvent extends PersistentEvent
  case class FundsBlockedEvent(fundsId: ExchangeId, amount: FiatAmount) extends StateEvent
  case class FundsMarkedUsedEvent(fundsId: ExchangeId, amount: FiatAmount) extends StateEvent
  case class FundsUnmarkedUsedEvent(fundsId: ExchangeId, amount: FiatAmount) extends StateEvent
  case class FundsUnblockedEvent(fundsId: ExchangeId) extends StateEvent

  case class Snapshot(funds: Map[ExchangeId, BlockedFundsInfo]) extends PersistentEvent
  case class BlockedFundsInfo(id: ExchangeId, remainingAmount: FiatAmount) {
    val timestamp = DateTime.now()
    def canUseFunds(amount: FiatAmount): Boolean = amount <= remainingAmount
  }
}

private[okpay] class BlockedFiatRegistryImpl extends BlockedFiatRegistry {
  import BlockedFiatRegistry._

  // Transient information
  private var balances = FiatAmounts.empty
  private var remainingLimits = FiatAmounts.empty
  private val fundsAvailability = new BlockedFundsAvailability()

  // Information saved in mementos
  private var funds: Map[ExchangeId, BlockedFundsInfo] = Map.empty

  override def updateTransientAmounts(
      newBalances: FiatAmounts, newRemainingLimits: FiatAmounts): Unit = {
    balances = newBalances
    remainingLimits = newRemainingLimits
    updateBackedFunds()
  }

  override def takeMemento: Map[ExchangeId, BlockedFundsInfo] = funds

  override def restoreMemento(snapshot: Map[ExchangeId, BlockedFundsInfo]): Unit = {
    funds = snapshot
    funds.keys.foreach(fundsAvailability.addFunds)
    updateBackedFunds()
  }

  override def blockedFundsByCurrency: FiatAmounts = {
    val fundsByCurrency = funds.values
        .groupBy(_.remainingAmount.currency)
        .mapValues(funds => funds.map(_.remainingAmount).reduce(_ + _))
    FiatAmounts(fundsByCurrency.values.toSeq)
  }

  override def notifyAvailabilityChanges(listener: AvailabilityListener): Unit = {
    fundsAvailability.notifyChanges(listener)
  }

  override def block(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    require(!funds.contains(fundsId))
    funds += fundsId -> BlockedFundsInfo(fundsId, amount)
    fundsAvailability.addFunds(fundsId)
    updateBackedFunds()
  }

  override def unblock(fundsId: ExchangeId): Unit = {
    funds -= fundsId
    fundsAvailability.removeFunds(fundsId)
    updateBackedFunds()
  }

  override def canMarkUsed(
      fundsId: ExchangeId, amount: FiatAmount): Validation[String, BlockedFundsInfo] = for {
    funds <- requireExistingFunds(fundsId, amount.currency)
    _ <- requireEnoughBalance(funds, amount)
    _ <- requiredBackedFunds(funds.id)
  } yield funds

  override def markUsed(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    val fundsToUse = funds(fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount - amount))
    balances = balances.decrement(amount)
    updateBackedFunds()
  }

  override def canUnmarkUsed(fundsId: ExchangeId, amount: FiatAmount) =
    requireExistingFunds(fundsId, amount.currency).map(_ => {})

  override def unmarkUsed(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    val fundsToUse = funds(fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount + amount))
    balances = balances.increment(amount)
    updateBackedFunds()
  }

  override def contains(fundsId: ExchangeId): Boolean = funds.contains(fundsId)

  private def updateBackedFunds(): Unit = {
    fundsAvailability.clearAvailable()
    for (currency <- currenciesInUse();
         funds <- fundsThatCanBeBacked(currency)) {
      fundsAvailability.setAvailable(funds)
    }
  }

  private def currenciesInUse(): Set[FiatCurrency] =
    funds.values.map(_.remainingAmount.currency).toSet

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
    val availableBalance = balances.getOrZero(currency)
    val remainingLimit = remainingLimits.get(currency)
    remainingLimit.fold(availableBalance)(_ min availableBalance)
  }

  private def updateFunds(newFunds: BlockedFundsInfo): Unit = {
    funds += newFunds.id -> newFunds
  }

  private def requireExistingFunds(
      fundsId: ExchangeId, currency: FiatCurrency): Validation[String, BlockedFundsInfo] =
    funds.get(fundsId).toSuccess(s"no funds with id $fundsId")
        .ensure(s"cannot spend $currency out of $fundsId")(_.remainingAmount.currency == currency)

  private def requireEnoughBalance(
      funds: BlockedFundsInfo, minimumBalance: FiatAmount): Validation[String, Unit] =
    if (funds.remainingAmount >= minimumBalance) ().success
    else (s"insufficient blocked funds for id ${funds.id}: " +
        s"$minimumBalance requested, ${funds.remainingAmount} available").failure

  private def requiredBackedFunds(fundsId: ExchangeId): Validation[String, Unit] =
    if (fundsAvailability.areAvailable(fundsId)) ().success
    else s"funds with id $fundsId are not currently available".failure
}

