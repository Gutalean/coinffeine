package coinffeine.protocol.messages.arbitration

import coinffeine.model.Both
import coinffeine.model.bitcoin.Hash
import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class CommitmentNotification(
  exchangeId: ExchangeId,
  bothCommitments: Both[Hash]
) extends PublicMessage
