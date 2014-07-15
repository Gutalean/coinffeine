package coinffeine.protocol.messages.arbitration

import coinffeine.model.exchange.{Both, Exchange}
import coinffeine.model.bitcoin.Hash
import coinffeine.protocol.messages.PublicMessage

case class CommitmentNotification(
  exchangeId: Exchange.Id,
  bothCommitments: Both[Hash]
) extends PublicMessage
