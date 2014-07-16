package coinffeine.protocol.messages.handshake

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.exchange.Exchange

class PeerHandshakeTest extends UnitTest {

  "Peer handshake message" should "not contain private keys" in {
    val completeKey = new KeyPair()
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerHandshake(Exchange.Id("id"), completeKey, "okpay:123")
    }
  }
}
