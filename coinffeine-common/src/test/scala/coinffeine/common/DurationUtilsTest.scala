package coinffeine.common

import scala.concurrent.duration._

import coinffeine.common.test.UnitTest

class DurationUtilsTest extends UnitTest {

  "A duration" should "be considered positive when finite and greater than zero" in {
    DurationUtils.requirePositive(1.second, "timeout")
  }

  it should "be considered positive when positive infinite" in {
    DurationUtils.requirePositive(Duration.Inf, "timeout")
    an [IllegalArgumentException] shouldBe thrownBy {
      DurationUtils.requirePositive(Duration.MinusInf, "timeout")
    }
  }

  it should "not be considered positive when being zero" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      DurationUtils.requirePositive(0.seconds, "timeout")
    }
    an [IllegalArgumentException] shouldBe thrownBy {
      DurationUtils.requirePositive(0.millis, "timeout")
    }
  }

  it should "not be considered positive when undefined" in {
    val ex = the [IllegalArgumentException] thrownBy {
      DurationUtils.requirePositive(Duration.Undefined, "some timeout")
    }
    ex.getMessage should include ("some timeout is not a positive duration")
  }
}
