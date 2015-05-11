package coinffeine.model.market

import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.order.OrderType

private[market] case class PositionQueue[T <: OrderType, C <: FiatCurrency](
    positions: Seq[Position[T, C]]) {

  require(positions.map(_.price).toSet.size < 2, "Different prices on the same position queue")

  def contains(positionId: PositionId): Boolean = positions.exists(_.id == positionId)

  def enqueue(position: Position[T, C]): PositionQueue[T, C] = {
    require(!contains(position.id), s"Position ${position.id} already queued")
    copy(positions :+ position)
  }

  def removeByPositionId(id: PositionId): PositionQueue[T, C] =
    copy(positions.filterNot(_.id == id))

  def removeByPeerId(peerId: PeerId): PositionQueue[T, C] =
    copy(positions.filterNot(_.id.peerId == peerId))

  def startHandshake(positionId: PositionId, crossedAmount: Bitcoin.Amount): PositionQueue[T, C] =
    copy(positions.collect {
      case position if position.id == positionId => position.startHandshake(crossedAmount)
      case otherPosition => otherPosition
    })

  def clearHandshake(positionId: PositionId, crossedAmount: Bitcoin.Amount): PositionQueue[T, C] =
    copy(positions.collect {
      case position if position.id == positionId => position.clearHandshake(crossedAmount)
      case otherPositions => otherPositions
    })

  def decreaseAmount(id: PositionId, amount: Bitcoin.Amount): PositionQueue[T, C] =
    copy(positions.collect {
      case position @ Position(_, _, _, `id`, _) if position.amount > amount =>
        position.decreaseAmount(amount)
      case position if position.id != id => position
    })

  def isEmpty: Boolean = positions.isEmpty

  def sum: Bitcoin.Amount = positions.map(_.amount).sum
}

private[market] object PositionQueue {

  def empty[T <: OrderType, C <: FiatCurrency](orderType: T, currency: C) =
    PositionQueue[T, C](Seq.empty)

  def empty[T <: OrderType, C <: FiatCurrency] = PositionQueue[T, C](Seq.empty)
}
