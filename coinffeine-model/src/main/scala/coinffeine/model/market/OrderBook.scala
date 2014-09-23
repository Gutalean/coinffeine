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
  def isCrossed: Boolean = spread.isCrossed

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: Spread[C] = Spread(highestBid, lowestAsk)

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
  def addPosition(position: BidOrAskPosition[C]): OrderBook[C] =
    position.fold(bid = addBidPosition, ask = addAskPosition)

  def addPositions(positions: Seq[BidOrAskPosition[C]]): OrderBook[C] =
    positions.foldLeft(this)(_.addPosition(_))

  def addBidPosition(position: BidPosition[C]): OrderBook[C] =
    copy(bids = bids.enqueuePosition(position))

  def addAskPosition(position: AskPosition[C]): OrderBook[C] =
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
  private def crosses(bids: Stream[BidPosition[C]],
                      asks: Stream[AskPosition[C]],
                      accum: Seq[Cross[C]]): Seq[Cross[C]] = {
    (bids.headOption, asks.headOption) match {
      case (None, _) | (_, None) => accum

      case (Some(bid), Some(ask)) if bid.price.underbids(ask.price) => accum

      case (Some(bid), Some(ask)) =>
        crosses(bids.tail, asks.tail, accum :+ crossAmount(bid, ask, bid.amount min ask.amount))
    }
  }

  private def crossAmount(bid: BidPosition[C],
                          ask: AskPosition[C],
                          amount: BitcoinAmount): Cross[C] =
    Cross(amount, bid.price.averageWith(ask.price), Both(bid.id, ask.id))

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
                                      price: Price[C],
                                      positions: Both[PositionId])

  def apply[C <: FiatCurrency](position: BidOrAskPosition[C],
                               otherPositions: BidOrAskPosition[C]*): OrderBook[C] =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty[C <: FiatCurrency](currency: C): OrderBook[C] = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency),
    handshakes = Map.empty[ExchangeId, Cross[C]]
  )
}
