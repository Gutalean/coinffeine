package coinffeine.protocol.messages.handshake

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class RefundSignatureRequest(exchangeId: ExchangeId, refundTx: ImmutableTransaction)
  extends PublicMessage
