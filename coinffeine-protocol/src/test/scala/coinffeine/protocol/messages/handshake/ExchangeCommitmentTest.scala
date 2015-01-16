package coinffeine.protocol.messages.handshake

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.exchange.ExchangeId

class ExchangeCommitmentTest extends UnitTest {

  val tx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
  val keyPair = new KeyPair()

  "An exchange commitment" should "prevent private key publication" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ExchangeCommitment(ExchangeId.random(), keyPair, tx)
    }
  }

  it should "have a compact string representation" in {
    ExchangeCommitment(ExchangeId.random(), keyPair.publicKey, tx).toString should
      fullyMatch regex "ExchangeCommitment(.*, key=.*, tx=.*)"
  }
}
