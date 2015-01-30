package coinffeine.model.market

import com.typesafe.scalalogging.StrictLogging

import coinffeine.model.currency._
import coinffeine.model.network.PeerId

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook[C <: FiatCurrency](bids: BidMap[C],
                                        asks: AskMap[C],
                                        handshakes: Set[Cross[C]]) {
  require(bids.currency == asks.currency)

  def userPositions(userId: PeerId): Seq[Position[_, C]] =
    bids.userPositions(userId) ++ asks.userPositions(userId)

  /** Get current spread (interval between the highest bet price to the lowest bid price */
  def spread: Spread[C] = Spread(highestBid, lowestAsk)

  private def highestBid: Option[Price[C]] = bids.bestPrice

  private def lowestAsk: Option[Price[C]] = asks.bestPrice

  def startHandshake(cross: Cross[C]): OrderBook[C] = copy(
    bids = bids.startHandshake(cross.positions.buyer),
    asks = asks.startHandshake(cross.positions.seller),
    handshakes = handshakes + cross
  )

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

  def get(positionId: PositionId): Option[Position[_ <: OrderType, C]] =
    bids.get(positionId) orElse asks.get(positionId)

  def decreaseAmount(positionId: PositionId, amount: Bitcoin.Amount): OrderBook[C] = copy(
    bids = bids.decreaseAmount(positionId, amount),
    asks = asks.decreaseAmount(positionId, amount)
  )

  def updateUserPositions(entries: Seq[OrderBookEntry[C]], userId: PeerId): OrderBook[C] = {
    val previousPositionIds = userPositions(userId).map(_.id).toSet
    val newPositionIds = entries.map(entryId => PositionId(userId, entryId.id))
    val positionsToRemove = previousPositionIds -- newPositionIds
    addOrUpdatePositions(entries, userId).cancelPositions(positionsToRemove.toSeq)
  }

  private def addOrUpdatePositions(entries: Seq[OrderBookEntry[C]], userId: PeerId): OrderBook[C] = {
    entries.foldLeft(this) { (book, entry) =>
      val positionId = PositionId(userId, entry.id)

      def bookWithPosition = {
        val limitPrice = entry.price.toOption.getOrElse(
          throw new IllegalArgumentException(s"Unsupported price: ${entry.price}"))
        book.addPosition(Position(entry.orderType, entry.amount, limitPrice, positionId))
      }

      def updatePosition(currentPosition: BidOrAskPosition[C]) = {
        logInvalidPositionChanges(entry, currentPosition)
        if (currentPosition.amount <= entry.amount) book
        else book.decreaseAmount(currentPosition.id, currentPosition.amount - entry.amount)
      }

      book.get(positionId).fold(bookWithPosition)(updatePosition)
    }
  }

  private def logInvalidPositionChanges(newEntry: OrderBookEntry[C],
                                        currentPosition: BidOrAskPosition[C]): Unit = {
    val invalidChanges = Seq(
      if (newEntry.orderType != currentPosition.orderType) Some("different order type") else None,
      if (newEntry.price != LimitPrice(currentPosition.price)) Some("different price") else None,
      if (newEntry.amount > currentPosition.amount) Some("amount increased") else None
    ).flatten
    if (invalidChanges.nonEmpty) {
      OrderBook.Log.warn("{} is an invalid update for {}: {}", newEntry, currentPosition,
        invalidChanges.mkString(", "))
    }
  }

  def anonymizedEntries: Seq[OrderBookEntry[C]] =
    bids.anonymizedEntries ++ asks.anonymizedEntries

  def clearHandshake(cross: Cross[C]): OrderBook[C] = {
    require(handshakes.contains(cross))
    copy(
      bids = bids.clearHandshake(cross.positions.buyer),
      asks = asks.clearHandshake(cross.positions.seller),
      handshakes = handshakes - cross
    )
  }
}

object OrderBook extends StrictLogging {
  private val Log = logger

  def apply[C <: FiatCurrency](position: BidOrAskPosition[C],
                               otherPositions: BidOrAskPosition[C]*): OrderBook[C] =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty[C <: FiatCurrency](currency: C): OrderBook[C] = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency),
    handshakes = Set.empty[Cross[C]]
  )
}
