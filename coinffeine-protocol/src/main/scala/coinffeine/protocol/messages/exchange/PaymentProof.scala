package coinffeine.protocol.messages.exchange

import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class PaymentProof(exchangeId: ExchangeId, paymentId: String, step: Int) extends PublicMessage
