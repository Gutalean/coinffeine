package coinffeine.model.market

import coinffeine.model.network.PeerId
import coinffeine.model.order.OrderId

case class PositionId(peerId: PeerId, orderId: OrderId)
