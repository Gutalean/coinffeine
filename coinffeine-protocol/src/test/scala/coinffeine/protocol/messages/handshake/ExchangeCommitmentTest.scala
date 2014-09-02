package coinffeine.protocol.messages.handshake

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{KeyPair, MutableTransaction, ImmutableTransaction}
import coinffeine.model.exchange.ExchangeId

class ExchangeCommitmentTest extends UnitTest {

  "An exchange commitment" should "prevent private key publication" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ExchangeCommitment(
        ExchangeId("id"),
        new KeyPair(),
        ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
      )
    }
  }
}
