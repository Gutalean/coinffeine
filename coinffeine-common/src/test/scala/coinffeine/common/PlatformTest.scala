package coinffeine.common

import coinffeine.common.test.UnitTest

class PlatformTest extends UnitTest {

  "Platform detection" should "detect Mac platforms" in withOsName("Mac OS X") {
    Platform.detect() shouldBe Platform.Mac
  }

  it should "detect Windows platforms" in withOsName("Windows 8") {
    Platform.detect() shouldBe Platform.Windows
  }

  it should "detect Linux platforms" in withOsName("Linux") {
    Platform.detect() shouldBe Platform.Linux
  }

  it should "throw when detecting unsupported platforms" in withOsName("Finux") {
    an [IllegalStateException] shouldBe thrownBy {
      Platform.detect()
    }
  }

  private def withOsName(value: String)(block: => Unit): Unit = {
    val originalValue = System.getProperty("os.name")
    try {
      System.setProperty("os.name", value)
      block
    } finally {
      System.setProperty("os.name", originalValue)
    }
  }
}
