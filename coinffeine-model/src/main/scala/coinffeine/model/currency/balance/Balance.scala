package coinffeine.model.currency.balance

import coinffeine.model.currency._

case class BitcoinBalance(
    estimated: BitcoinAmount,
    available: BitcoinAmount,
    minOutput: Option[BitcoinAmount],
    blocked: BitcoinAmount = Bitcoin.zero) {

  def amount = estimated
}

object BitcoinBalance {

  val empty = BitcoinBalance(
    estimated = Bitcoin.zero,
    available = Bitcoin.zero,
    minOutput = None)

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalances(
    amounts: FiatAmounts,
    blockedAmounts: FiatAmounts,
    remainingLimits: FiatAmounts) {

  def balanceFor(currency: FiatCurrency) = FiatBalance(
    amount = amounts.getOrZero(currency),
    blockedAmount = blockedAmounts.getOrZero(currency),
    remainingLimit = remainingLimits.get(currency)
  )
}

object FiatBalances {
  val empty = FiatBalances(FiatAmounts.empty, FiatAmounts.empty, FiatAmounts.empty)
}

case class FiatBalance(
    amount: FiatAmount,
    blockedAmount: FiatAmount,
    remainingLimit: Option[FiatAmount]) {

  require(currenciesNumber == 1, s"Balance with mixed currencies: $this")

  private def currenciesNumber: Int =
    (Set(amount.currency, blockedAmount.currency) ++ remainingLimit.map(_.currency)).size
}
