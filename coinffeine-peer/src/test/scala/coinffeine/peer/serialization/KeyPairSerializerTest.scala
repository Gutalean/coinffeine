package coinffeine.peer.serialization

import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork

class KeyPairSerializerTest extends SerializerTest {

  "Key pairs" should "support roundtrip Kryo serialization" in {
    val keyPair = new KeyPair()
    KeyPairUtils.equals(keyPair, keyPairSerializationRoundtrip(keyPair)) shouldBe true
  }

  "Public keys" should "support roundtrip Kryo serialization" in {
    val publicKey = new KeyPair().publicKey
    KeyPairUtils.equals(publicKey, keyPairSerializationRoundtrip(publicKey)) shouldBe true
  }

  "Deterministic keys" should "support roundtrip Kryo serialization" in {
    val wallet = new Wallet(CoinffeineUnitTestNetwork)
    val keyPair = wallet.freshReceiveKey()
    keyPair.canSign shouldBe true
    val deserializedKeyPair = keyPairSerializationRoundtrip(keyPair)
    KeyPairUtils.equals(keyPair, deserializedKeyPair) shouldBe true
    deserializedKeyPair.canSign shouldBe true
  }
}
