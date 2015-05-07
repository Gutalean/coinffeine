package coinffeine.protocol.messages.handshake

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class ExchangeRejection (
  exchangeId: ExchangeId,
  reason: String
) extends PublicMessage

object ExchangeRejection {

  def apply(exchangeId: ExchangeId, cause: Cause): ExchangeRejection =
    ExchangeRejection(exchangeId, cause.message)

  sealed trait Cause {
    val message: String
  }

  case object CounterpartTimeout extends Cause {
    override val message = "Timeout waiting for a valid signature"
  }

  case object UnavailableFunds extends Cause {
    override val message = "Not enough available funds"
  }

  case object InvalidOrderMatch extends Cause {
    override val message = "Invalid order match"
  }
}
