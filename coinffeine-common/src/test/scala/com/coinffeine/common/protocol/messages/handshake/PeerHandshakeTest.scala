package com.coinffeine.common.protocol.messages.handshake

import com.coinffeine.common.bitcoin.KeyPair
import com.coinffeine.common.exchange.Exchange
import com.coinffeine.common.test.UnitTest

class PeerHandshakeTest extends UnitTest {

  "Peer handshake message" should "not contain private keys" in {
    val completeKey = new KeyPair()
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerHandshake(Exchange.Id("id"), completeKey, "okpay:123")
    }
  }
}
