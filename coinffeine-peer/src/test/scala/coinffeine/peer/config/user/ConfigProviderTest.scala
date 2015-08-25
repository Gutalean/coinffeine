package coinffeine.peer.config.user

import java.io.File
import scala.collection.JavaConversions._
import scala.concurrent.duration._

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually

import coinffeine.common.test.UnitTest
import coinffeine.peer.bitcoin.BitcoinSettings
import coinffeine.peer.config.{ConfigProvider, InMemoryConfigStore, SettingsMapping}

class ConfigProviderTest extends UnitTest with BeforeAndAfterAll with Eventually {

  "User file config provider" should "retrieve fresh user config file" in new Fixture {
    provider.userConfig shouldBe ConfigFactory.empty()
  }

  it should "save some user config" in new Fixture {
    val cfg = ConfigFactory.parseString("my.prop = 7")
    provider.saveUserConfig(cfg)
    store.readConfig() shouldBe cfg
  }

  it should "refresh config after save user settings" in new Fixture {
    val cfg = ConfigFactory.parseString("my.prop = 7")
    provider.saveUserConfig(cfg)
    provider.enrichedConfig.getInt("my.prop") shouldBe 7
  }

  it should "drop user config item while saving when it matches the reference one" in new Fixture {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg)
    store.readConfig().hasPath(sampleRefConfigItem.getKey) shouldBe false
  }

  it should "not drop user config item while saving when its modified" in new Fixture {
    val replacedValue = ConfigValueFactory.fromAnyRef("__this is another value__")
    val cfg = ConfigFactory.empty().withValue(sampleRefConfigItem.getKey, replacedValue)
    provider.saveUserConfig(cfg)
    store.readConfig().hasPath(sampleRefConfigItem.getKey) shouldBe true
    store.readConfig().getValue(sampleRefConfigItem.getKey) shouldBe replacedValue
  }

  it should "not drop user config item while saving when not instructed to drop" in new Fixture {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg, dropReferenceValues = false)
    store.readConfig().hasPath(sampleRefConfigItem.getKey) shouldBe true
    store.readConfig().getValue(sampleRefConfigItem.getKey) shouldBe sampleRefConfigItem.getValue
  }

  it should "save user settings" in new Fixture {
    val settings = FoobarSettings("Mr. Potato", 123456)
    provider.saveUserSettings(settings)(foobarSettingsMapping)

    provider.userConfig.getString("foobar.potato") shouldBe "Mr. Potato"
    provider.userConfig.getInt(sampleRefConfigItem.getKey) shouldBe 123456
    provider.userConfig.hasPath(otherSampleRefConfigItem.getKey) shouldBe false
  }

  it should "make settings react to config updates" in new Fixture {
    val settings = provider.generalSettings().copy(serviceStartStopTimeout = 5.days)
    provider.saveUserSettings(settings)
    eventually { provider.generalSettingsProperty.get.serviceStartStopTimeout shouldBe 5.days }
  }

  trait Fixture {
    val dataPath = new File("/home/user")
    val store = new InMemoryConfigStore(ConfigFactory.empty(), dataPath)
    val provider = new ConfigProvider(store)
    val configFile = new File(dataPath, "testing-file")
    val sampleRefConfigItem = provider.referenceConfig.root().entrySet().head
    val otherSampleRefConfigItem = provider.referenceConfig.root().entrySet().last

    case class FoobarSettings(potato: String, length: Int)

    object foobarSettingsMapping extends SettingsMapping[FoobarSettings] {

      override def fromConfig(configPath: File, config: Config) = ???

      override def toConfig(settings: FoobarSettings, config: Config) = config
        .withValue("foobar.potato", ConfigValueFactory.fromAnyRef(settings.potato))
        .withValue(sampleRefConfigItem.getKey, ConfigValueFactory.fromAnyRef(settings.length))
        .withValue(otherSampleRefConfigItem.getKey, otherSampleRefConfigItem.getValue)
    }

  }
}
