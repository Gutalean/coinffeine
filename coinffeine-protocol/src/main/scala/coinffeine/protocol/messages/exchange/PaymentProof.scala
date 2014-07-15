package coinffeine.protocol.messages.exchange

import coinffeine.model.exchange.Exchange
import coinffeine.protocol.messages.PublicMessage

case class PaymentProof(exchangeId: Exchange.Id, paymentId: String) extends PublicMessage
