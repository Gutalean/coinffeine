package coinffeine.peer.payment.okpay.blocking

import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.std.option._
import scalaz.syntax.validation._

import coinffeine.model.currency.{FiatAmount, FiatCurrency, FiatAmounts}
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistryActor.BlockedFundsInfo

private[okpay] class BlockedFiatRegistry {
  // Transient information
  private var balances = FiatAmounts.empty
  private var remainingLimits = FiatAmounts.empty
  private val fundsAvailability = new BlockedFundsAvailability()

  // Information saved in mementos
  private var funds: Map[ExchangeId, BlockedFundsInfo] = Map.empty

  def updateTransientAmounts(newBalances: FiatAmounts, newRemainingLimits: FiatAmounts): Unit = {
    balances = newBalances
    remainingLimits = newRemainingLimits
    updateBackedFunds()
  }

  def takeMemento: Map[ExchangeId, BlockedFundsInfo] = funds

  def restoreMemento(snapshot: Map[ExchangeId, BlockedFundsInfo]): Unit = {
    funds = snapshot
    funds.keys.foreach(fundsAvailability.addFunds)
    updateBackedFunds()
  }

  def blockedFundsByCurrency: FiatAmounts = {
    val fundsByCurrency = funds.values
        .groupBy(_.remainingAmount.currency)
        .mapValues(funds => funds.map(_.remainingAmount).reduce(_ + _))
    FiatAmounts(fundsByCurrency.values.toSeq)
  }

  def notifyAvailabilityChanges(listener: AvailabilityListener): Unit = {
    fundsAvailability.notifyChanges(listener)
  }

  def block(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    require(!funds.contains(fundsId))
    funds += fundsId -> BlockedFundsInfo(fundsId, amount)
    fundsAvailability.addFunds(fundsId)
    updateBackedFunds()
  }

  def unblock(fundsId: ExchangeId): Unit = {
    funds -= fundsId
    fundsAvailability.removeFunds(fundsId)
    updateBackedFunds()
  }

  def canMarkUsed(
      fundsId: ExchangeId, amount: FiatAmount): Validation[String, BlockedFundsInfo] = for {
    funds <- requireExistingFunds(fundsId, amount.currency)
    _ <- requireEnoughBalance(funds, amount)
    _ <- requiredBackedFunds(funds.id)
  } yield funds

  def markUsed(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    val fundsToUse = funds(fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount - amount))
    balances = balances.decrement(amount)
    updateBackedFunds()
  }

  def canUnmarkUsed(fundsId: ExchangeId, amount: FiatAmount) =
    requireExistingFunds(fundsId, amount.currency)

  def unmarkUsed(fundsId: ExchangeId, amount: FiatAmount): Unit = {
    val fundsToUse = funds(fundsId)
    updateFunds(fundsToUse.copy(remainingAmount = fundsToUse.remainingAmount + amount))
    balances = balances.increment(amount)
    updateBackedFunds()
  }

  def contains(fundsId: ExchangeId): Boolean = funds.contains(fundsId)

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
    val availableBalance = balances.get(currency).getOrElse(currency.zero)
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

