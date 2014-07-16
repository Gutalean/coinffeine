package coinffeine.protocol.messages.handshake

import coinffeine.model.bitcoin.PublicKey
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.protocol.messages.PublicMessage

case class PeerHandshake(
    exchangeId: ExchangeId,
    publicKey: PublicKey,
    paymentProcessorAccount: AccountId) extends PublicMessage {
  require(!publicKey.hasPrivKey, s"$publicKey includes a private key")
}
