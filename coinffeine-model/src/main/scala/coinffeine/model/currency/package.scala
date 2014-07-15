package coinffeine.model

package object currency {
  type BitcoinAmount = CurrencyAmount[Currency.Bitcoin.type]
  type FiatAmount = CurrencyAmount[FiatCurrency]
}
