package coinffeine.protocol.messages.handshake

import coinffeine.model.exchange.Exchange
import coinffeine.protocol.messages.PublicMessage

case class ExchangeRejection (
  exchangeId: Exchange.Id,
  reason: String
) extends PublicMessage
