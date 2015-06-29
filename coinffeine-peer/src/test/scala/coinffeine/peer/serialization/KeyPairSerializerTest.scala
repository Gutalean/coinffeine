package coinffeine.peer.serialization

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class KeyPairSerializerTest extends SerializerTest {

  "Key pairs" should "support roundtrip Kryo serialization" in new Fixture {
    val keyPair = new KeyPair()
    KeyPairUtils.equals(keyPair, serializationRoundtrip(keyPair)) shouldBe true
  }

  "Public keys" should "support roundtrip Kryo serialization" in new Fixture {
    val publicKey = new KeyPair().publicKey
    KeyPairUtils.equals(publicKey, serializationRoundtrip(publicKey)) shouldBe true
  }

  "Deterministic keys" should "support roundtrip Kryo serialization" in new Fixture {
    val wallet = new Wallet(CoinffeineUnitTestNetwork)
    val keyPair = wallet.freshReceiveKey()
    keyPair.canSign shouldBe true
    val deserializedKeyPair = serializationRoundtrip[KeyPair](keyPair)
    KeyPairUtils.equals(keyPair, deserializedKeyPair) shouldBe true
    deserializedKeyPair.canSign shouldBe true
  }
}
