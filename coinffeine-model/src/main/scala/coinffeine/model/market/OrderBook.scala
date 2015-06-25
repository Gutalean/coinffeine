package coinffeine.model.market

import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.order.{Ask, Bid, OrderType, Price}

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook(bids: BidMap, asks: AskMap) {
  require(bids.currency == asks.currency)

  def userPositions(userId: PeerId): Seq[Position[_]] =
    bids.userPositions(userId) ++ asks.userPositions(userId)

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: Spread = Spread(highestBid, lowestAsk)

  private def highestBid: Option[Price] = bids.bestPrice

  private def lowestAsk: Option[Price] = asks.bestPrice

  def startHandshake(cross: Cross): OrderBook = copy(
    bids = bids.startHandshake(cross.positions.buyer, cross.bitcoinAmounts.buyer),
    asks = asks.startHandshake(cross.positions.seller, cross.bitcoinAmounts.seller)
  )

  /** Add a new position if not yet added
    *
    * @param position  Position to add
    * @return          New order book
    */
  def addPosition(position: BidOrAskPosition): OrderBook =
    if (get(position.id).isDefined) this
    else position.fold(bid = addBidPosition, ask = addAskPosition)

  def addPositions(positions: Seq[BidOrAskPosition]): OrderBook =
    positions.foldLeft(this)(_.addPosition(_))

  def addBidPosition(position: BidPosition): OrderBook =
    copy(bids = bids.enqueuePosition(position))

  def addAskPosition(position: AskPosition): OrderBook =
    copy(asks = asks.enqueuePosition(position))

  def cancelPosition(positionId: PositionId): OrderBook =
    copy(bids = bids.cancelPosition(positionId), asks = asks.cancelPosition(positionId))

  def cancelPositions(positionIds: Seq[PositionId]): OrderBook =
    positionIds.foldLeft(this)(_.cancelPosition(_))

  def get(positionId: PositionId): Option[Position[_ <: OrderType]] =
    bids.get(positionId) orElse asks.get(positionId)

  /** Clear handshake and decrement pending positions amount */
  def completeSuccessfulHandshake(cross: Cross): OrderBook = clearHandshake(cross)
    .decreaseAmount(cross.positions.buyer, cross.bitcoinAmounts.buyer)
    .decreaseAmount(cross.positions.seller, cross.bitcoinAmounts.seller)

  /** Clear handshake and drop culprit positions */
  def completeFailedHandshake(cross: Cross, culprits: Set[PeerId]): OrderBook = {
    val positionsToDrop = cross.positions.toSeq.filter { position =>
      culprits.contains(position.peerId)
    }
    clearHandshake(cross).cancelPositions(positionsToDrop)
  }

  private def clearHandshake(cross: Cross): OrderBook = copy(
    bids = bids.clearHandshake(cross.positions.buyer, cross.bitcoinAmounts.buyer),
    asks = asks.clearHandshake(cross.positions.seller, cross.bitcoinAmounts.seller)
  )

  private def decreaseAmount(positionId: PositionId, amount: BitcoinAmount): OrderBook = copy(
    bids = bids.decreaseAmount(positionId, amount),
    asks = asks.decreaseAmount(positionId, amount)
  )

  def updateUserPositions(entries: Seq[OrderBookEntry], userId: PeerId): OrderBook = {
    val previousPositionIds = userPositions(userId).map(_.id).toSet
    val newPositionIds = entries.map(entryId => PositionId(userId, entryId.id)).toSet
    val idsToRemove = previousPositionIds -- newPositionIds
    addPositions(entries.map(toPosition(userId, _))).cancelPositions(idsToRemove.toSeq)
  }

  private def toPosition(userId: PeerId, entry: OrderBookEntry): Position[_ <: OrderType] =
    Position(entry.orderType, entry.amount, entry.price, PositionId(userId, entry.id))

  def anonymizedEntries: Seq[OrderBookEntry] =
    bids.anonymizedEntries ++ asks.anonymizedEntries
}

object OrderBook {

  def apply(position: BidOrAskPosition, otherPositions: BidOrAskPosition*): OrderBook =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty(currency: FiatCurrency): OrderBook = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency)
  )
}
