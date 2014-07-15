package com.coinffeine.common.protocol.messages.handshake

import coinffeine.model.exchange.Exchange
import coinffeine.model.bitcoin.ImmutableTransaction
import com.coinffeine.common.protocol.messages.PublicMessage

case class RefundSignatureRequest(exchangeId: Exchange.Id, refundTx: ImmutableTransaction)
  extends PublicMessage
