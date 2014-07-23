package coinffeine.model

package object currency {
  type BitcoinAmount = CurrencyAmount[Currency.Bitcoin.type]

  type FiatAmount = CurrencyAmount[FiatCurrency]
  object FiatAmount {
    def apply(value: BigDecimal, currencyCode: String): FiatAmount =
      FiatCurrency(currencyCode).amount(value)
  }
}
