package coinffeine.peer.config

import scala.collection.JavaConversions._

import com.typesafe.config.{Config, ConfigValueFactory, ConfigFactory}
import org.scalatest.BeforeAndAfterAll

import coinffeine.common.test.UnitTest
import coinffeine.peer.config.user.UserFileConfigProvider

class UserFileConfigProviderTest extends UnitTest with BeforeAndAfterAll {

  val provider = UserFileConfigProvider("testing-file")
  val configFile = provider.userConfigFile().toFile
  val sampleRefConfigItem = provider.referenceConfig.root().entrySet().head
  val otherSampleRefConfigItem = provider.referenceConfig.root().entrySet().last

  override def afterAll(): Unit = {
    configFile.delete()
  }

  "User file config provider" should "retrieve fresh user config file" in {
    provider.userConfigFile().toFile should be ('file)
    provider.userConfig should be (ConfigFactory.empty())
  }

  it should "save some user config" in {
    val cfg = ConfigFactory.parseString("my.prop = 7")
    provider.saveUserConfig(cfg)
    configFile.length() should be > 0l
  }

  it should "refresh config after save user settings" in {
    provider.config.getInt("my.prop") shouldBe 7
  }

  it should "drop user config item while saving when it matches the reference one" in {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) should be (false)
  }

  it should "not drop user config item while saving when its modified" in {
    val replacedValue = ConfigValueFactory.fromAnyRef("__this is another value__")
    val cfg = ConfigFactory.empty().withValue(sampleRefConfigItem.getKey, replacedValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) should be (true)
    provider.userConfig.getValue(sampleRefConfigItem.getKey) should be (replacedValue)
  }

  it should "not drop user config item while saving when not instructed to drop" in {
    val cfg = ConfigFactory.empty()
      .withValue(sampleRefConfigItem.getKey, sampleRefConfigItem.getValue)
    provider.saveUserConfig(cfg, dropReferenceValues = false)
    provider.userConfig.hasPath(sampleRefConfigItem.getKey) should be (true)
    provider.userConfig.getValue(sampleRefConfigItem.getKey) should be (sampleRefConfigItem.getValue)
  }

  it should "save user settings" in {
    val settings = FoobarSettings("Mr. Potato", 123456)
    provider.saveUserSettings(settings)

    provider.userConfig.getString("foobar.potato") should be ("Mr. Potato")
    provider.userConfig.getInt(sampleRefConfigItem.getKey) should be (123456)
    provider.userConfig.hasPath(otherSampleRefConfigItem.getKey) should be (false)
  }

  case class FoobarSettings(potato: String, length: Int)

  implicit lazy val foobarSettingsMapping = new SettingsMapping[FoobarSettings] {

    override def fromConfig(config: Config) = ???

    override def toConfig(settings: FoobarSettings, config: Config) = config
      .withValue("foobar.potato", ConfigValueFactory.fromAnyRef(settings.potato))
      .withValue(sampleRefConfigItem.getKey, ConfigValueFactory.fromAnyRef(settings.length))
      .withValue(otherSampleRefConfigItem.getKey, otherSampleRefConfigItem.getValue)
  }
}
