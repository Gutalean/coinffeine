package coinffeine.model.market

import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId
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

  def startHandshake(exchangeId: ExchangeId, positionId: PositionId): PositionQueue[T, C] =
    copy(positions.collect {
      case position if position.id == positionId => position.copy(handshake = Some(exchangeId))
      case otherPosition => otherPosition
    })

  def completeHandshake(exchangeId: ExchangeId, amount: BitcoinAmount): PositionQueue[T, C] =
    copy(positions.collect {
      case position if position.handshake == Some(exchangeId) && position.amount > amount =>
        position.decreaseAmount(amount).copy(handshake = None)
      case otherPositions if otherPositions.handshake != Some(exchangeId) => otherPositions
    })

  def cancelHandshake(exchangeId: ExchangeId): PositionQueue[T, C] = copy(positions.collect {
    case position if position.handshake == Some(exchangeId) => position.copy(handshake = None)
    case otherPositions => otherPositions
  })

  def decreaseAmount(id: PositionId, amount: BitcoinAmount): PositionQueue[T, C] =
    copy(positions.collect {
      case position @ Position(_, _, _, `id`, _) if position.amount > amount =>
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
