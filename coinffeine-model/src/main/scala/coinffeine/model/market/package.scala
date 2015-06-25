package coinffeine.model

import coinffeine.model.order.{Ask, Bid, OrderType}

package object market {
  type BidMap = OrderMap[Bid.type]
  type AskMap = OrderMap[Ask.type]
  type BidPosition = Position[Bid.type]
  type AskPosition = Position[Ask.type]
  type BidOrAskPosition = Position[_ <: OrderType]
}
