package coinffeine.protocol.messages.handshake

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class ExchangeRejection (
  exchangeId: ExchangeId,
  reason: String
) extends PublicMessage
