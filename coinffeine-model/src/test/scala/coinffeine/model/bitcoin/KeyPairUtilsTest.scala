package coinffeine.model.bitcoin

import coinffeine.common.test.UnitTest

class KeyPairUtilsTest extends UnitTest {

  "Key pairs" should "be equal when comparing null values" in {
    KeyPairUtils.equals(null, null) shouldBe true
  }

  it should "not be equal when comparing null with non-null values" in {
    val key = new KeyPair()
    KeyPairUtils.equals(null, key) shouldBe false
    KeyPairUtils.equals(key, null) shouldBe false
  }

  it should "compare their encodings for pairs of non-null values" in {
    val k1, k2 = new KeyPair()
    KeyPairUtils.equals(k1, k1) shouldBe true
    KeyPairUtils.equals(k1, k2) shouldBe false
  }
}
