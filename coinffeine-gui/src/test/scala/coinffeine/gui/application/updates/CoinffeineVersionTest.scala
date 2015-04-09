package coinffeine.gui.application.updates

import coinffeine.common.test.UnitTest

class CoinffeineVersionTest extends UnitTest {

  "Coinffeine version" should "check whether it is newer than other" in {
    CoinffeineVersion(1, 0, 1).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe false
    CoinffeineVersion(1, 0, 0).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe false
    CoinffeineVersion(0, 1, 0).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe false
    CoinffeineVersion(1, 0, 2).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe true
    CoinffeineVersion(1, 1, 0).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe true
    CoinffeineVersion(2, 0, 0).isNewerThan(CoinffeineVersion(1, 0, 1)) shouldBe true
    CoinffeineVersion(1, 0, 1).isNewerThan(CoinffeineVersion(1, 0, 1, "SNAPSHOT")) shouldBe true
    CoinffeineVersion(1, 0, 0, "alpha").isNewerThan(CoinffeineVersion(1, 0, 0, "beta")) shouldBe false
    CoinffeineVersion(0, 7, 0).isNewerThan(CoinffeineVersion(0, 8, 0, "SNAPSHOT")) shouldBe false
  }

  it should "be parsed from string" in {
    CoinffeineVersion("1.0.1") shouldBe CoinffeineVersion(1, 0, 1)
    CoinffeineVersion("2.1") shouldBe CoinffeineVersion(2, 1, 0)
    CoinffeineVersion("1.2.3-foo") shouldBe CoinffeineVersion(1, 2, 3, "foo")
    CoinffeineVersion("1.2-foo") shouldBe CoinffeineVersion(1, 2, 0, "foo")
  }

  it should "not be parsed from an invalid string" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      CoinffeineVersion("1-0-0")
    }
  }
}
