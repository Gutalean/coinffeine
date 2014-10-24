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
  }
}
