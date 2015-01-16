package coinffeine.protocol.messages.handshake

import org.bitcoinj.core.Utils

import coinffeine.model.bitcoin._
import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class ExchangeCommitment(
  exchangeId: ExchangeId,
  publicKey: PublicKey,
  commitmentTransaction: ImmutableTransaction
) extends PublicMessage {
  require(!publicKey.canSign, "Just the public key is needed")

  override def toString =
    "ExchangeCommitment(%s, key=%s, tx=%s)"
      .format(exchangeId, Utils.HEX.encode(publicKey.getPubKey), commitmentTransaction.get.getHash)
}
