package coinffeine.protocol.messages.handshake

import coinffeine.model.exchange.Exchange
import coinffeine.protocol.messages.PublicMessage

case class ExchangeAborted (
  exchangeId: Exchange.Id,
  reason: String
) extends PublicMessage
