package coinffeine.protocol.messages.handshake

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.exchange.Exchange
import coinffeine.protocol.messages.PublicMessage

case class ExchangeCommitment(
  exchangeId: Exchange.Id,
  commitmentTransaction: ImmutableTransaction
) extends PublicMessage
