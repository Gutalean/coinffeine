package com.coinffeine.common.protocol.messages.arbitration

import coinffeine.model.exchange.{Both, Exchange}
import coinffeine.model.bitcoin.Hash
import com.coinffeine.common.protocol.messages.PublicMessage

case class CommitmentNotification(
  exchangeId: Exchange.Id,
  bothCommitments: Both[Hash]
) extends PublicMessage
