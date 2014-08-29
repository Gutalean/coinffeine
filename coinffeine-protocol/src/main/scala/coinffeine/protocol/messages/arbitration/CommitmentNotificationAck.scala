package coinffeine.protocol.messages.arbitration

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class CommitmentNotificationAck(exchangeId: ExchangeId) extends PublicMessage
