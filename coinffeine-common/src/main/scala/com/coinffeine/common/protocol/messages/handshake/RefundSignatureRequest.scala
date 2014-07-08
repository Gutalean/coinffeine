package com.coinffeine.common.protocol.messages.handshake

import com.coinffeine.common.bitcoin.ImmutableTransaction
import com.coinffeine.common.exchange.Exchange
import com.coinffeine.common.protocol.messages.PublicMessage

case class RefundSignatureRequest(exchangeId: Exchange.Id, refundTx: ImmutableTransaction)
  extends PublicMessage
