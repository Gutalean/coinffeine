package coinffeine.model.order

import java.util.UUID

case class OrderId(value: String) {
  override def toString = s"order $value"

  /** Return a shorten string representation of this order ID. */
  def toShortString = toString.takeWhile(_ != '-') + "..."
}

object OrderId {
  def random(): OrderId = OrderId(UUID.randomUUID().toString)

  private val Pattern = """([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})""".r

  def parse(text: String): Option[OrderId] =
    Pattern.unapplySeq(text).map(groups => OrderId(groups.head))
}
