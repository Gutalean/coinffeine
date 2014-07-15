package com.coinffeine.common.protocol.messages.handshake

import coinffeine.model.exchange.Exchange
import coinffeine.model.bitcoin.ImmutableTransaction
import com.coinffeine.common.protocol.messages.PublicMessage

case class ExchangeCommitment(
  exchangeId: Exchange.Id,
  commitmentTransaction: ImmutableTransaction
) extends PublicMessage
