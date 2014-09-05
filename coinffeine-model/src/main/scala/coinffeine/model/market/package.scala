package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object market {
  type BidMap[C <: FiatCurrency] = OrderMap[Bid.type, C]
  type AskMap[C <: FiatCurrency] = OrderMap[Ask.type, C]
}
