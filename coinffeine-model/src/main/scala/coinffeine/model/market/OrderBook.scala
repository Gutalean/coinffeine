package coinffeine.model.market

import coinffeine.model.currency._
import coinffeine.model.network.PeerId

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook[C <: FiatCurrency](bids: BidMap[C], asks: AskMap[C]) {
  require(bids.currency == asks.currency)

  def userPositions(userId: PeerId): Seq[Position[_, C]] =
    bids.userPositions(userId) ++ asks.userPositions(userId)

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: Spread[C] = Spread(highestBid, lowestAsk)

  private def highestBid: Option[Price[C]] = bids.bestPrice

  private def lowestAsk: Option[Price[C]] = asks.bestPrice

  def startHandshake(cross: Cross[C]): OrderBook[C] = copy(
    bids = bids.startHandshake(cross.positions.buyer, cross.bitcoinAmounts.buyer),
    asks = asks.startHandshake(cross.positions.seller, cross.bitcoinAmounts.seller)
  )

  /** Add a new position if not yet added
    *
    * @param position  Position to add
    * @return          New order book
    */
  def addPosition(position: BidOrAskPosition[C]): OrderBook[C] =
    if (get(position.id).isDefined) this
    else position.fold(bid = addBidPosition, ask = addAskPosition)

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

  def get(positionId: PositionId): Option[Position[_ <: OrderType, C]] =
    bids.get(positionId) orElse asks.get(positionId)

  /** Clear handshake and decrement pending positions amount */
  def completeSuccessfulHandshake(cross: Cross[C]): OrderBook[C] = clearHandshake(cross)
    .decreaseAmount(cross.positions.buyer, cross.bitcoinAmounts.buyer)
    .decreaseAmount(cross.positions.seller, cross.bitcoinAmounts.seller)

  /** Clear handshake and drop culprit positions */
  def completeFailedHandshake(cross: Cross[C], culprits: Set[PeerId]): OrderBook[C] = {
    val positionsToDrop = cross.positions.toSeq.filter { position =>
      culprits.contains(position.peerId)
    }
    clearHandshake(cross).cancelPositions(positionsToDrop)
  }

  private def clearHandshake(cross: Cross[C]): OrderBook[C] = copy(
    bids = bids.clearHandshake(cross.positions.buyer, cross.bitcoinAmounts.buyer),
    asks = asks.clearHandshake(cross.positions.seller, cross.bitcoinAmounts.seller)
  )

  private def decreaseAmount(positionId: PositionId, amount: Bitcoin.Amount): OrderBook[C] = copy(
    bids = bids.decreaseAmount(positionId, amount),
    asks = asks.decreaseAmount(positionId, amount)
  )

  def updateUserPositions(entries: Seq[OrderBookEntry[C]], userId: PeerId): OrderBook[C] = {
    val previousPositionIds = userPositions(userId).map(_.id).toSet
    val newPositionIds = entries.map(entryId => PositionId(userId, entryId.id)).toSet
    val idsToRemove = previousPositionIds -- newPositionIds
    addPositions(entries.map(toPosition(userId, _))).cancelPositions(idsToRemove.toSeq)
  }

  private def toPosition(userId: PeerId, entry: OrderBookEntry[C]): Position[_ <: OrderType, C] =
    Position(entry.orderType, entry.amount, entry.price, PositionId(userId, entry.id))

  def anonymizedEntries: Seq[OrderBookEntry[C]] =
    bids.anonymizedEntries ++ asks.anonymizedEntries
}

object OrderBook {

  def apply[C <: FiatCurrency](position: BidOrAskPosition[C],
                               otherPositions: BidOrAskPosition[C]*): OrderBook[C] =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty[C <: FiatCurrency](currency: C): OrderBook[C] = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency)
  )
}
