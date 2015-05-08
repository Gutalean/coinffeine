package coinffeine.protocol.messages.handshake

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class ExchangeRejection (exchangeId: ExchangeId, cause: ExchangeRejection.Cause)
  extends PublicMessage

object ExchangeRejection {

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
