package coinffeine.model

import coinffeine.model.currency.FiatCurrency

package object market {

  type AnyCurrencyOrder = Order[_ <: FiatCurrency]

  type AnyPrice = Price[_ <: FiatCurrency]
  type AnyOrderPrice = OrderPrice[_ <: FiatCurrency]

  type BidMap[C <: FiatCurrency] = OrderMap[Bid.type, C]
  type AskMap[C <: FiatCurrency] = OrderMap[Ask.type, C]
  type BidPosition[C <: FiatCurrency] = Position[Bid.type, C]
  type AskPosition[C <: FiatCurrency] = Position[Ask.type, C]
  type BidOrAskPosition[C <: FiatCurrency] = Position[_ <: OrderType, C]
}
