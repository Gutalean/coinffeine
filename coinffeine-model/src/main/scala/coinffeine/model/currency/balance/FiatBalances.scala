package coinffeine.model.currency.balance

import java.util.NoSuchElementException

import coinffeine.model.currency.{FiatAmount, FiatCurrency}

/** Available amounts and remaining transference limits if any */
case class FiatBalances private(values: Map[FiatCurrency, FiatBalances.Balance]) {

  def apply(currency: FiatCurrency): FiatBalances.Balance =
    values.getOrElse(currency,
      throw new NoSuchElementException(s"Missing balance for $currency in $this"))

  def get(currency: FiatCurrency): Option[FiatBalances.Balance] = values.get(currency)

  def withBalance(amount: FiatAmount, remainingLimit: FiatAmount): FiatBalances = {
    FiatBalances.requireSameCurrencies(amount, remainingLimit)
    withBalance(amount, Some(remainingLimit))
  }

  def withAmount(amount: FiatAmount): FiatBalances = withBalance(amount, remainingLimit = None)

  private def withBalance(
      amount: FiatAmount, remainingLimit: Option[FiatAmount]): FiatBalances = {
    copy(values = values +
        (amount.currency -> FiatBalances.Balance(amount, remainingLimit)))
  }
}

object FiatBalances {

  case class Balance(amount: FiatAmount, remainingLimit: Option[FiatAmount])

  val empty = new FiatBalances(Map.empty)

  def fromBalances(pairs: (FiatAmount, FiatAmount)*): FiatBalances = {
    requireNonDuplicatedCurrencies(pairs.map(_._1))
    pairs.foreach(requireSameCurrencies _ tupled)
    new FiatBalances(pairs.map {
      case (a, l) => a.currency -> new Balance(a, Some(l))
    }.toMap)
  }

  def fromAmounts(amounts: FiatAmount*): FiatBalances = {
    requireNonDuplicatedCurrencies(amounts)
    new FiatBalances(amounts.map(a => a.currency -> new Balance(a, None)).toMap)
  }

  private def requireNonDuplicatedCurrencies(amounts: Seq[FiatAmount]): Unit = {
    require(!duplicatedCurrencies(amounts),
      s"fiat amounts with duplicated currencies: $amounts")
  }

  private def requireSameCurrencies(amount: FiatAmount, limit: FiatAmount): Unit = {
    require(amount.currency == limit.currency,
      s"currency mismatch in amount and limit: $amount and $limit")
  }

  private def duplicatedCurrencies(amounts: Seq[FiatAmount]) =
    amounts.map(_.currency).distinct.size != amounts.size
}

case class CachedFiatBalances(value: FiatBalances, status: CacheStatus)

object CachedFiatBalances {
  def fresh(value: FiatBalances) = CachedFiatBalances(value, CacheStatus.Fresh)
  def stale(value: FiatBalances) = CachedFiatBalances(value, CacheStatus.Stale)
}
