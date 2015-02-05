package coinffeine.model.market

import scala.collection.immutable.{SortedMap, TreeMap}

import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.network.PeerId

/** Data structure that holds orders sorted by price and, within a given price, keep
  * them sorted with a FIFO policy. */
case class OrderMap[T <: OrderType, C <: FiatCurrency] (
    orderType: T, currency: C, tree: SortedMap[Price[C], PositionQueue[T, C]]) {

  type Queue = PositionQueue[T, C]
  type Pos = Position[T, C]

  def enqueuePosition(position: Pos): OrderMap[T, C] = {
    require(get(position.id).isEmpty, s"Position ${position.id} already enqueued")
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

  def bestPrice: Option[Price[C]] = positionsNotCompletelyInHandshake.headOption.map(_.price)

  def decreaseAmount(id: PositionId, amount: Bitcoin.Amount): OrderMap[T, C] =
    get(id).fold(this) { position =>
      updateQueue(position.price, queue => queue.decreaseAmount(id, amount))
    }

  def cancelPosition(positionId: PositionId): OrderMap[T, C] =
    mapQueues(_.removeByPositionId(positionId))

  def cancelPositions(peerId: PeerId): OrderMap[T, C] = mapQueues(_.removeByPeerId(peerId))

  def startHandshake(positionId: PositionId, crossedAmount: Bitcoin.Amount): OrderMap[T, C] =
    mapQueues(_.startHandshake(positionId, crossedAmount))

  def clearHandshake(positionId: PositionId, crossedAmount: Bitcoin.Amount): OrderMap[T, C] =
    mapQueues(_.clearHandshake(positionId, crossedAmount))

  def anonymizedEntries: Seq[OrderBookEntry[C]] = for {
    queue <- tree.values.toSeq
    position <- queue.positions
  } yield position.toOrderBookEntry

  private def updateQueue(price: Price[C], f: Queue => Queue): OrderMap[T, C] = {
    val modifiedQueue = f(tree.getOrElse(price, PositionQueue.empty[T, C]))
    if (modifiedQueue.isEmpty) copy(tree = tree - price)
    else copy(tree = tree.updated(price, modifiedQueue))
  }

  private def mapQueues(f: Queue => Queue): OrderMap[T, C] =
    copy(tree = removeEmptyQueues(tree.mapValues(f)))

  private def removeEmptyQueues(tree: SortedMap[Price[C], Queue]) = tree.filter {
    case (_, queue) => queue.positions.nonEmpty
  }
}

object OrderMap {

  /** Empty order map */
  def empty[T <: OrderType, C <: FiatCurrency](orderType: T, currency: C): OrderMap[T, C] =
    OrderMap(orderType, currency, TreeMap.empty(orderType.priceOrdering[C]))

  def apply[T <: OrderType, C <: FiatCurrency](
      first: Position[T, C], other: Position[T, C]*): OrderMap[T, C] = {
    val positions = first +: other
    val accumulator: OrderMap[T, C] = empty(first.orderType, positions.head.price.currency)
    positions.foldLeft(accumulator)(_.enqueuePosition(_))
  }
}
