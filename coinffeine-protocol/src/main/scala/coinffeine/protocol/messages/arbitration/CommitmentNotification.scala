package coinffeine.protocol.messages.arbitration

import coinffeine.model.bitcoin.Hash
import coinffeine.model.exchange.{Both, ExchangeId}
import coinffeine.protocol.messages.PublicMessage

case class CommitmentNotification(
  exchangeId: ExchangeId,
  bothCommitments: Both[Hash]
) extends PublicMessage
