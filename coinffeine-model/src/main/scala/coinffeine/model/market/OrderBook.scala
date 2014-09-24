package coinffeine.model.market

import scala.annotation.tailrec

import org.slf4j.LoggerFactory

import coinffeine.model.currency._
import coinffeine.model.exchange.Both
import coinffeine.model.network.PeerId

/** Represents a snapshot of a continuous double auction (CDA) */
case class OrderBook[C <: FiatCurrency](bids: BidMap[C],
                                        asks: AskMap[C],
                                        handshakes: Set[Cross[C]]) {
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

  def startHandshake(cross: Cross[C]): OrderBook[C] = {
    require(crosses.contains(cross))
    copy(
      bids = bids.startHandshake(cross.positions.buyer),
      asks = asks.startHandshake(cross.positions.seller),
      handshakes = handshakes + cross
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

  def updateUserPositions(entries: Seq[OrderBookEntry[C]], userId: PeerId): OrderBook[C] = {
    val previousPositionIds = userPositions(userId).map(_.id).toSet
    val newPositionIds = entries.map(entryId => PositionId(userId, entryId.id))
    val positionsToRemove = previousPositionIds -- newPositionIds
    addOrUpdatePositions(entries, userId).cancelPositions(positionsToRemove.toSeq)
  }

  private def addOrUpdatePositions(entries: Seq[OrderBookEntry[C]], userId: PeerId): OrderBook[C] = {
    entries.foldLeft(this) { (book, entry) =>
      val positionId = PositionId(userId, entry.id)

      def bookWithPosition = book.addPosition(
        Position(entry.orderType, entry.amount, entry.price, positionId))

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
      if (newEntry.price != currentPosition.price) Some("different price") else None,
      if (newEntry.amount > currentPosition.amount) Some("amount increased") else None
    ).flatten
    if (invalidChanges.nonEmpty) {
      Log.warn("{} is an invalid update for {}: {}", newEntry, currentPosition,
        invalidChanges.mkString(", "))
    }
  }

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
                          amount: BitcoinAmount): Cross[C] = {
    val averagePrice = bid.price.averageWith(ask.price)
    Cross(Both.fill(amount), Both.fill(averagePrice.of(amount)), Both(bid.id, ask.id))
  }

  def completeHandshake(cross: Cross[C]): OrderBook[C] = {
    require(handshakes.contains(cross))
    copy(
      bids = bids.completeHandshake(cross.positions.buyer, cross.bitcoinAmounts.buyer),
      asks = asks.completeHandshake(cross.positions.seller, cross.bitcoinAmounts.seller),
      handshakes = handshakes - cross
    )
  }

  def clearHandshake(cross: Cross[C]): OrderBook[C] = {
    require(handshakes.contains(cross))
    copy(
      bids = bids.clearHandshake(cross.positions.buyer),
      asks = asks.clearHandshake(cross.positions.seller),
      handshakes = handshakes - cross
    )
  }
}

object OrderBook {
  private val Log = LoggerFactory.getLogger(classOf[OrderBook[_]])

  def apply[C <: FiatCurrency](position: BidOrAskPosition[C],
                               otherPositions: BidOrAskPosition[C]*): OrderBook[C] =
    empty(position.price.currency).addPositions(position +: otherPositions)

  def empty[C <: FiatCurrency](currency: C): OrderBook[C] = OrderBook(
    bids = OrderMap.empty(Bid, currency),
    asks = OrderMap.empty(Ask, currency),
    handshakes = Set.empty[Cross[C]]
  )
}
