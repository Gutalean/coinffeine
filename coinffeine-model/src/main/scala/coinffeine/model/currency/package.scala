package coinffeine.model

package object currency extends Implicits {
  type FiatAmount = CurrencyAmount[_ <: FiatCurrency]
  object FiatAmount {
    def apply(value: BigDecimal, currencyCode: String): FiatAmount =
      CurrencyAmount.exactAmount(value, FiatCurrency(currencyCode))
  }

  type AnyFiatBalance = FiatBalance[_ <: FiatCurrency]
}
