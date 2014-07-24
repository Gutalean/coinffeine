package coinffeine.model.exchange

import java.util.UUID

/** An identifier for a exchange. */
case class ExchangeId(value: String) {
  override def toString = s"exchange $value"
}

object ExchangeId {

  def random() = ExchangeId(value = UUID.randomUUID().toString)
}
