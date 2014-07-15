package coinffeine.model.market

import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.network.PeerId

private[market] case class PositionQueue[T <: OrderType, C <: FiatCurrency](
    positions: Seq[Position[T, C]]) {

  require(positions.map(_.price).toSet.size < 2, "Different prices on the same position queue")

  def contains(positionId: PositionId): Boolean = positions.exists(_.id == positionId)

  def enqueue(position: Position[T, C]): PositionQueue[T, C] = {
    require(!contains(position.id), s"Position ${position.id} already enqueued")
    copy(positions :+ position)
  }

  def removeByPositionId(id: PositionId): PositionQueue[T, C] =
    copy(positions.filterNot(_.id == id))

  def removeByPeerId(peerId: PeerId): PositionQueue[T, C] =
    copy(positions.filterNot(_.id.peerId == peerId))

  def decreaseAmount(id: PositionId, amount: BitcoinAmount): PositionQueue[T, C] =
    copy(positions.collect {
      case position @ Position(_, previousAmount, _, `id`) if previousAmount > amount =>
        position.decreaseAmount(amount)
      case position if position.id != id => position
    })

  def isEmpty: Boolean = positions.isEmpty

  def sum: BitcoinAmount = positions.map(_.amount).foldLeft(0.BTC)(_ + _)
}

private[market] object PositionQueue {

  def empty[T <: OrderType, C <: FiatCurrency](orderType: T, currency: C) =
    PositionQueue[T, C](Seq.empty)

  def empty[T <: OrderType, C <: FiatCurrency] = PositionQueue[T, C](Seq.empty)
}
