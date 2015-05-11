package coinffeine.model

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.order.{Ask, Bid, OrderType}

package object market {
  type BidMap[C <: FiatCurrency] = OrderMap[Bid.type, C]
  type AskMap[C <: FiatCurrency] = OrderMap[Ask.type, C]
  type BidPosition[C <: FiatCurrency] = Position[Bid.type, C]
  type AskPosition[C <: FiatCurrency] = Position[Ask.type, C]
  type BidOrAskPosition[C <: FiatCurrency] = Position[_ <: OrderType, C]
}
