package coinffeine.model.bitcoin

import coinffeine.common.test.UnitTest

class PimpMyKeyPairTest extends UnitTest {

  val keyPair = new KeyPair()
  val publicKey = keyPair.publicKey

  "Pimp my key pair" should "get the public key from a key pair" in {
    publicKey.publicKey shouldBe publicKey
  }

  it should "get just the public key from a key pair" in {
    publicKey shouldBe 'pubKeyOnly
    publicKey.canSign shouldBe false
  }

  it should "be idempotent when extracting public keys" in {
    publicKey.getPubKey shouldEqual keyPair.getPubKey
  }
}
