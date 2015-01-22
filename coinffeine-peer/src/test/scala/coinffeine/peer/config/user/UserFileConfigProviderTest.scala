package coinffeine.peer.config.user

import java.io.File
import scala.collection.JavaConversions._

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.BeforeAndAfterAll

import coinffeine.common.test.{TempDir, UnitTest}
import coinffeine.peer.config.SettingsMapping

class UserFileConfigProviderTest extends UnitTest with BeforeAndAfterAll {

  private val dataPath = TempDir.create("dataDir")
  private val provider = new UserFileConfigProvider(dataPath, "testing-file")
  private val configFile = new File(dataPath, "testing-file")
  private val sampleRefConfigItem = provider.referenceConfig.root().entrySet().head
  private val otherSampleRefConfigItem = provider.referenceConfig.root().entrySet().last

  override def afterAll(): Unit = {
    configFile.delete()
  }

  "User file config provider" should "retrieve fresh user config file" in {
    provider.userConfig shouldBe ConfigFactory.empty()
  }

  it should "save some user config" in {
    val cfg = ConfigFactory.parseString("my.prop = 7")
    provider.saveUserConfig(cfg)
    configFile.length() should be > 0l
  }

  it should "refresh config after save user settings" in {
    provider.enrichedConfig.getInt("my.prop") shouldBe 7
  }

  it should "drop user config item while saving when it matches the reference one" in {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) shouldBe false
  }

  it should "not drop user config item while saving when its modified" in {
    val replacedValue = ConfigValueFactory.fromAnyRef("__this is another value__")
    val cfg = ConfigFactory.empty().withValue(sampleRefConfigItem.getKey, replacedValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) shouldBe true
    provider.userConfig.getValue(sampleRefConfigItem.getKey) shouldBe replacedValue
  }

  it should "not drop user config item while saving when not instructed to drop" in {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg, dropReferenceValues = false)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) shouldBe true
    provider.userConfig.getValue(sampleRefConfigItem.getKey) shouldBe sampleRefConfigItem.getValue
  }

  it should "save user settings" in {
    val settings = FoobarSettings("Mr. Potato", 123456)
    provider.saveUserSettings(settings)

    provider.userConfig.getString("foobar.potato") shouldBe "Mr. Potato"
    provider.userConfig.getInt(sampleRefConfigItem.getKey) shouldBe 123456
    provider.userConfig.hasPath(otherSampleRefConfigItem.getKey) shouldBe false
  }

  case class FoobarSettings(potato: String, length: Int)

  implicit lazy val foobarSettingsMapping = new SettingsMapping[FoobarSettings] {

    override def fromConfig(configPath: File, config: Config) = ???

    override def toConfig(settings: FoobarSettings, config: Config) = config
      .withValue("foobar.potato", ConfigValueFactory.fromAnyRef(settings.potato))
      .withValue(sampleRefConfigItem.getKey, ConfigValueFactory.fromAnyRef(settings.length))
      .withValue(otherSampleRefConfigItem.getKey, otherSampleRefConfigItem.getValue)
  }
}
