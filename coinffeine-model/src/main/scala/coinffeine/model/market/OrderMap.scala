package coinffeine.model.market

import scala.collection.immutable.{SortedMap, TreeMap}

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.network.PeerId
import coinffeine.model.order.{LimitPrice, OrderPrice, OrderType, Price}

/** Data structure that holds orders sorted by price and, within a given price, keep
  * them sorted with a FIFO policy. */
case class OrderMap[T <: OrderType] (
    orderType: T, currency: FiatCurrency, tree: SortedMap[OrderPrice, PositionQueue[T]]) {

  type Queue = PositionQueue[T]
  type Pos = Position[T]

  def enqueuePosition(position: Pos): OrderMap[T] = {
    require(get(position.id).isEmpty, s"Position ${position.id} already queued")
    updateQueue(position.price, queue => queue.enqueue(position))
  }

  /** Sorted client positions */
  def positions: Iterable[Pos] = tree.values.flatMap(_.positions)

  def positionsNotCompletelyInHandshake: Iterable[Pos] = positions.filter { pos =>
    pos.amount > pos.handshakingAmount
  }

  def userPositions(userId: PeerId): Seq[Pos] =
    positions.filter(_.id.peerId == userId).toSeq

  def get(positionId: PositionId): Option[Pos] = positions.find(_.id == positionId)

  def bestPrice: Option[Price] = positionsNotCompletelyInHandshake.collectFirst {
    case Position(_, _, LimitPrice(price), _, _) => price
  }

  def decreaseAmount(id: PositionId, amount: BitcoinAmount): OrderMap[T] =
    get(id).fold(this) { position =>
      updateQueue(position.price, queue => queue.decreaseAmount(id, amount))
    }

  def cancelPosition(positionId: PositionId): OrderMap[T] =
    mapQueues(_.removeByPositionId(positionId))

  def cancelPositions(peerId: PeerId): OrderMap[T] = mapQueues(_.removeByPeerId(peerId))

  def startHandshake(positionId: PositionId, crossedAmount: BitcoinAmount): OrderMap[T] =
    mapQueues(_.startHandshake(positionId, crossedAmount))

  def clearHandshake(positionId: PositionId, crossedAmount: BitcoinAmount): OrderMap[T] =
    mapQueues(_.clearHandshake(positionId, crossedAmount))

  def anonymizedEntries: Seq[OrderBookEntry] = for {
    queue <- tree.values.toSeq
    position <- queue.positions
  } yield position.toOrderBookEntry

  private def updateQueue(price: OrderPrice, f: Queue => Queue): OrderMap[T] = {
    val modifiedQueue = f(tree.getOrElse(price, PositionQueue.empty[T]))
    if (modifiedQueue.isEmpty) copy(tree = tree - price)
    else copy(tree = tree.updated(price, modifiedQueue))
  }

  private def mapQueues(f: Queue => Queue): OrderMap[T] =
    copy(tree = removeEmptyQueues(tree.mapValues(f)))

  private def removeEmptyQueues(tree: SortedMap[OrderPrice, Queue]) = tree.filter {
    case (_, queue) => queue.positions.nonEmpty
  }
}

object OrderMap {

  /** Empty order map */
  def empty[T <: OrderType](orderType: T, currency: FiatCurrency): OrderMap[T] =
    OrderMap(orderType, currency, TreeMap.empty(orderType.priceOrdering))

  def apply[T <: OrderType, C <: FiatCurrency](
      first: Position[T], other: Position[T]*): OrderMap[T] = {
    val positions = first +: other
    val accumulator: OrderMap[T] = empty(first.orderType, positions.head.price.currency)
    positions.foldLeft(accumulator)(_.enqueuePosition(_))
  }
}
