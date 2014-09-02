package coinffeine.model.market

import scala.annotation.tailrec

import coinffeine.model.currency._
import coinffeine.model.exchange.{ExchangeId, Both}
import coinffeine.model.market.OrderBook.Cross
import coinffeine.model.network.PeerId

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook[C <: FiatCurrency](bids: BidMap[C],
                                        asks: AskMap[C],
                                        handshakes: Map[ExchangeId, Cross[C]]) {
  import coinffeine.model.market.OrderBook._
  require(bids.currency == asks.currency)

  def userPositions(userId: PeerId): Seq[Position[_, C]] =
    bids.userPositions(userId) ++ asks.userPositions(userId)

  /** Tells if a transaction is possible with current orders. */
  def isCrossed: Boolean = spread match {
    case (Some(bidPrice), Some(askPrice)) if bidPrice >= askPrice => true
    case _ => false
  }

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: Spread[C] = highestBid -> lowestAsk

  private def highestBid: Option[Price[C]] = bids.bestPrice

  private def lowestAsk: Option[Price[C]] = asks.bestPrice

  def startHandshake(exchangeId: ExchangeId, cross: Cross[C]): OrderBook[C] = {
    require(crosses.contains(cross))
    copy(
      bids = bids.startHandshake(exchangeId, cross.positions.buyer),
      asks = asks.startHandshake(exchangeId, cross.positions.seller),
      handshakes = handshakes + (exchangeId -> cross)
    )
  }

  /** Add a new position
    *
    * @param position  Position to add
    * @return          New order book
    */
  def addPosition(position: Position[_ <: OrderType, C]): OrderBook[C] =
    position.fold(bid = addBidPosition, ask = addAskPosition)

  def addPositions(positions: Seq[Position[_ <: OrderType, C]]): OrderBook[C] =
    positions.foldLeft(this)(_.addPosition(_))

  def addBidPosition(position: Position[Bid.type, C]): OrderBook[C] =
    copy(bids = bids.enqueuePosition(position))

  def addAskPosition(position: Position[Ask.type, C]): OrderBook[C] =
    copy(asks = asks.enqueuePosition(position))

  def cancelPosition(positionId: PositionId): OrderBook[C] =
    copy(bids = bids.cancelPosition(positionId), asks = asks.cancelPosition(positionId))

  def cancelPositions(positionIds: Seq[PositionId]): OrderBook[C] =
    positionIds.foldLeft(this)(_.cancelPosition(_))

  /** Cancel al orders of a given client */
  def cancelAllPositions(requester: PeerId): OrderBook[C] =
    copy(bids = bids.cancelPositions(requester), asks = asks.cancelPositions(requester))

  def get(positionId: PositionId): Option[Position[_ <: OrderType, C]] =
    bids.get(positionId) orElse asks.get(positionId)

  def decreaseAmount(positionId: PositionId, amount: BitcoinAmount): OrderBook[C] = copy(
    bids = bids.decreaseAmount(positionId, amount),
    asks = asks.decreaseAmount(positionId, amount)
  )

  def crosses: Seq[Cross[C]] = crosses(
    bids.positionsNotInHandshake.toStream,
    asks.positionsNotInHandshake.toStream,
    accum = Seq.empty
  )

  def anonymizedEntries: Seq[OrderBookEntry[C]] =
    bids.anonymizedEntries ++ asks.anonymizedEntries

  @tailrec
  private def crosses(bids: Stream[Position[Bid.type, C]],
                      asks: Stream[Position[Ask.type, C]],
                      accum: Seq[Cross[C]]): Seq[Cross[C]] = {
    (bids.headOption, asks.headOption) match {
      case (None, _) | (_, None) => accum

      case (Some(bid), Some(ask)) if bid.price < ask.price => accum

      case (Some(bid), Some(ask)) =>
        crosses(bids.tail, asks.tail, accum :+ crossAmount(bid, ask, bid.amount min ask.amount))
    }
  }

  private def crossAmount(bid: Position[Bid.type, C],
                          ask: Position[Ask.type, C],
                          amount: BitcoinAmount): Cross[C] =
    Cross(amount, (bid.price + ask.price) / 2, Both(bid.id, ask.id))

  def completeHandshake(exchangeId: ExchangeId): OrderBook[C] = {
    val cross = handshakes.getOrElse(exchangeId,
      throw new IllegalArgumentException(s"Unknown exchange $exchangeId"))
    copy(
      bids = bids.completeHandshake(exchangeId, cross.amount),
      asks = asks.completeHandshake(exchangeId, cross.amount),
      handshakes = handshakes - exchangeId
    )
  }

  def cancelHandshake(exchangeId: ExchangeId): OrderBook[C] = {
    require(handshakes.contains(exchangeId))
    copy(
      bids = bids.cancelHandshake(exchangeId),
      asks = asks.cancelHandshake(exchangeId),
      handshakes = handshakes - exchangeId
    )
  }
}

object OrderBook {

  case class Cross[C <: FiatCurrency](amount: BitcoinAmount,
                                      price: CurrencyAmount[C],
                                      positions: Both[PositionId])

  type Spread[C <: FiatCurrency] = (Option[CurrencyAmount[C]], Option[CurrencyAmount[C]])

  def apply[C <: FiatCurrency](position: Position[_ <: OrderType, C],
                               otherPositions: Position[_ <: OrderType, C]*): OrderBook[C] =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty[C <: FiatCurrency](currency: C): OrderBook[C] = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency),
    handshakes = Map.empty[ExchangeId, Cross[C]]
  )
}
