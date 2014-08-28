package coinffeine.protocol.messages.exchange

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class MicropaymentChannelClosed(exchangeId: ExchangeId) extends PublicMessage
