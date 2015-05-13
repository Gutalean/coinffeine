package coinffeine.peer.market.orders.archive.h2.serialization

import coinffeine.model.order.OrderStatus

object OrderStatusFormatter {
  def format(orderStatus: OrderStatus): String = orderStatus match {
    case product: Product => product.productPrefix
  }
}
