package com.coinffeine.common.protocol.messages.exchange

import coinffeine.model.exchange.Exchange
import com.coinffeine.common.protocol.messages.PublicMessage

case class PaymentProof(exchangeId: Exchange.Id, paymentId: String) extends PublicMessage
