package coinffeine.peer

import coinffeine.common.test.UnitTest

class AppVersionTest extends UnitTest {

  "Coinffeine version" should "check whether it is newer than other" in {
    AppVersion(1, 0, 1).isNewerThan(AppVersion(1, 0, 1)) shouldBe false
    AppVersion(1, 0, 0).isNewerThan(AppVersion(1, 0, 1)) shouldBe false
    AppVersion(0, 1, 0).isNewerThan(AppVersion(1, 0, 1)) shouldBe false
    AppVersion(1, 0, 2).isNewerThan(AppVersion(1, 0, 1)) shouldBe true
    AppVersion(1, 1, 0).isNewerThan(AppVersion(1, 0, 1)) shouldBe true
    AppVersion(2, 0, 0).isNewerThan(AppVersion(1, 0, 1)) shouldBe true
    AppVersion(1, 0, 1).isNewerThan(AppVersion(1, 0, 1, "SNAPSHOT")) shouldBe true
    AppVersion(1, 0, 0, "alpha").isNewerThan(AppVersion(1, 0, 0, "beta")) shouldBe false
    AppVersion(0, 7, 0).isNewerThan(AppVersion(0, 8, 0, "SNAPSHOT")) shouldBe false
  }

  it should "be parsed from string" in {
    AppVersion("1.0.1") shouldBe AppVersion(1, 0, 1)
    AppVersion("2.1") shouldBe AppVersion(2, 1, 0)
    AppVersion("1.2.3-foo") shouldBe AppVersion(1, 2, 3, "foo")
    AppVersion("1.2-foo") shouldBe AppVersion(1, 2, 0, "foo")
  }

  it should "not be parsed from an invalid string" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      AppVersion("1-0-0")
    }
  }
}
