package coinffeine.peer.serialization

import coinffeine.model.currency.FiatAmounts

class FiatAmountsSerializerTest extends SerializerTest {

  "An empty fiat amount" should "support roundtrip Kryo serialization" in new Fixture {
    val amounts = FiatAmounts.empty
    amounts shouldBe serializationRoundtrip(amounts)
  }
}
