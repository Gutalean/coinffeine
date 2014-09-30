package coinffeine.model

package object currency {
  type BitcoinAmount = CurrencyAmount[Bitcoin.type]

  type FiatAmount = CurrencyAmount[_ <: FiatCurrency]
  object FiatAmount {
    def apply(value: BigDecimal, currencyCode: String): FiatAmount =
      CurrencyAmount(value, FiatCurrency(currencyCode))
  }
}
