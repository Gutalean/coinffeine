package coinffeine.model

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

package object market {
  type Price[C <: FiatCurrency] = CurrencyAmount[C]
  type BidMap[C <: FiatCurrency] = OrderMap[Bid.type, C]
  type AskMap[C <: FiatCurrency] = OrderMap[Ask.type, C]
}
