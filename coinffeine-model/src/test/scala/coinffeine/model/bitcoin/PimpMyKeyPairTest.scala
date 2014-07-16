package coinffeine.model.bitcoin

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.Implicits._

class PimpMyKeyPairTest extends UnitTest {

  val keyPair = new KeyPair()
  val publicKey = keyPair.publicKey

  "Pimp my key pair" should "get the public key from a key pair" in {
    publicKey.publicKey should be(publicKey)
  }

  it should "get just the public key from a key pair" in {
    publicKey.hasPrivKey should be (false)
  }

  it should "be idempontent when extracting public keys" in {
    publicKey.getPubKey.sameElements(keyPair.getPubKey)
  }
}
