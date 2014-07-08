package com.coinffeine.common.protocol.messages.handshake

import com.coinffeine.common.bitcoin.PublicKey
import com.coinffeine.common.exchange.Exchange
import com.coinffeine.common.paymentprocessor.PaymentProcessor
import com.coinffeine.common.protocol.messages.PublicMessage

case class PeerHandshake(
    exchangeId: Exchange.Id,
    publicKey: PublicKey,
    paymentProcessorAccount: PaymentProcessor.AccountId) extends PublicMessage {
  require(!publicKey.hasPrivKey, s"$publicKey includes a private key")
}
