package coinffeine.gui.application.updates

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.OptionValues

import coinffeine.common.test.{FutureMatchers, UnitTest}

class ConfigVersionCheckerTest extends UnitTest with FutureMatchers with OptionValues {

  "Config version checker" should "process latest stable version from valid config" in {
    val factory = withConfigFactory(
      "latest-stable.major" -> 1,
      "latest-stable.minor" -> 2,
      "latest-stable.revision" -> 3,
      "latest-stable.build" -> "SNAPSHOT"
    )
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    stable.futureValue.major shouldBe 1
    stable.futureValue.minor shouldBe 2
    stable.futureValue.revision shouldBe 3
    stable.futureValue.tag.value shouldBe "SNAPSHOT"
  }

  it should "process latest stable version from config with missing build" in {
    val factory = withConfigFactory(
      "latest-stable.major" -> 1,
      "latest-stable.minor" -> 2,
      "latest-stable.revision" -> 3
    )
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    stable.futureValue.tag shouldBe 'empty
  }

  it should "fail to process latest stable version from config with missing major version" in {
    val factory = withConfigFactory(
      "latest-stable.minor" -> 2,
      "latest-stable.revision" -> 3
    )
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    an [VersionChecker.VersionFetchingException] should be thrownBy stable.futureValue
  }

  it should "fail to process latest stable version from config with missing minor version" in {
    val factory = withConfigFactory(
      "latest-stable.major" -> 1,
      "latest-stable.revision" -> 3
    )
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    an [VersionChecker.VersionFetchingException] should be thrownBy stable.futureValue
  }

  it should "fail to process latest stable version from config with missing revision version" in {
    val factory = withConfigFactory(
      "latest-stable.major" -> 1,
      "latest-stable.minor" -> 2
    )
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    an [VersionChecker.VersionFetchingException] should be thrownBy stable.futureValue
  }

  it should "fail to process latest stable version when config factory fails" in {
    val factory = withFailingConfigFactory(new Exception("Oh no! More Lemmings!"))
    val checker = new ConfigVersionChecker(factory)
    val stable = checker.latestStableVersion()
    an [VersionChecker.VersionFetchingException] should be thrownBy stable.futureValue
  }

  private def withConfigFactory(settings: (String, Any)*): ConfigVersionChecker.ConfigProvider =
    new ConfigVersionChecker.ConfigProvider {
      override def apply() = Future.successful(settings.foldLeft(ConfigFactory.empty()) {
        case (c, (k, v)) => c.withValue(k, ConfigValueFactory.fromAnyRef(v))
      })
      override def shutdown() = {}
    }

  private def withFailingConfigFactory(e: Throwable): ConfigVersionChecker.ConfigProvider =
    new ConfigVersionChecker.ConfigProvider {
      override def apply() = Future.failed(e)
      override def shutdown() = {}
    }
}
