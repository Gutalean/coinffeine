package coinffeine.model.market

import java.util.UUID

case class OrderId(value: String) {
  override def toString = s"order $value"

  /** Return a shorten string representation of this order ID. */
  def toShortString = toString.takeWhile(_ != '-') + "..."
}

object OrderId {
  def random(): OrderId = OrderId(UUID.randomUUID().toString)
}
