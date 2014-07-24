package coinffeine.model.market

import java.util.UUID

case class OrderId(value: String) {
  override def toString = s"order $value"
}

object OrderId {
  def random(): OrderId = OrderId(UUID.randomUUID().toString)
}
