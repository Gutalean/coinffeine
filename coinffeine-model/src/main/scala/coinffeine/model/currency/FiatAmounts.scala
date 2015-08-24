package coinffeine.model.currency

import java.util.NoSuchElementException

/** Available amounts and remaining transference limits if any */
case class FiatAmounts private(entries: Map[FiatCurrency, FiatAmount]) {

  def apply(currency: FiatCurrency): FiatAmount =
    entries.getOrElse(currency,
      throw new NoSuchElementException(s"Missing amount for $currency in $this"))

  def get(currency: FiatCurrency): Option[FiatAmount] = entries.get(currency)

  def getOrZero(currency: FiatCurrency): FiatAmount = get(currency).getOrElse(currency.zero)

  def amounts: Seq[FiatAmount] = entries.values.toSeq

  def contains(currency: FiatCurrency): Boolean = entries.contains(currency)

  def currencies: Set[FiatCurrency] = entries.keySet

  def increment(delta: FiatAmount): FiatAmounts = {
    val prevAmount = getOrZero(delta.currency)
    withAmount((prevAmount + delta) max delta.currency.zero)
  }

  def decrement(delta: FiatAmount): FiatAmounts = increment(-delta)

  def withAmount(amount: FiatAmount): FiatAmounts = copy(
    entries = entries + (amount.currency -> amount))

  def +(other: FiatAmounts): FiatAmounts = FiatAmounts(
    (amounts ++ other.amounts).groupBy(_.currency).transform { (currency, values) =>
      currency.sum(values)
    }
  )
}

object FiatAmounts {

  case class Balance(amount: FiatAmount, remainingLimit: Option[FiatAmount]) {
    def increment(delta: FiatAmount): Balance =
      copy(amount = (amount + delta) max amount.currency.zero)
  }

  val empty = new FiatAmounts(Map.empty)

  def apply(amounts: Seq[FiatAmount]): FiatAmounts = {
    requireNonDuplicatedCurrencies(amounts)
    new FiatAmounts(amounts.map(a => a.currency -> a).toMap)
  }

  def fromAmounts(amounts: FiatAmount*): FiatAmounts = apply(amounts)

  private def requireNonDuplicatedCurrencies(amounts: Seq[FiatAmount]): Unit = {
    require(!duplicatedCurrencies(amounts),
      s"fiat amounts with duplicated currencies: $amounts")
  }

  private def duplicatedCurrencies(amounts: Seq[FiatAmount]) =
    amounts.map(_.currency).distinct.size != amounts.size
}
