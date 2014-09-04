package coinffeine.peer.config

import scala.collection.JavaConversions._

import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import org.scalatest.BeforeAndAfterAll

import coinffeine.common.test.UnitTest

class FileConfigProviderTest extends UnitTest with BeforeAndAfterAll {

  val provider = FileConfigProvider("testing-file")
  val configFile = provider.userConfigFile().toFile

  override def afterAll(): Unit = {
    configFile.delete()
  }

  "File config provider" should "retrieve fresh user config file" in {
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
    val configItem = provider.referenceConfig.root().entrySet().head
    val cfg = ConfigFactory.empty().withValue(configItem.getKey, configItem.getValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(configItem.getKey) should be (false)
  }

  it should "not drop user config item while saving when its modified" in {
    val configItem = provider.referenceConfig.root().entrySet().head
    val replacedValue = ConfigValueFactory.fromAnyRef("__this is another value__")
    val cfg = ConfigFactory.empty().withValue(configItem.getKey, replacedValue)
    provider.saveUserConfig(cfg)
    provider.userConfig.hasPath(configItem.getKey) should be (true)
    provider.userConfig.getValue(configItem.getKey) should be (replacedValue)
  }

  it should "not drop user config item while saving when not instructed to drop" in {
    val configItem = provider.referenceConfig.root().entrySet().head
    val cfg = ConfigFactory.empty().withValue(configItem.getKey, configItem.getValue)
    provider.saveUserConfig(cfg, dropReferenceValues = false)
    provider.userConfig.hasPath(configItem.getKey) should be (true)
    provider.userConfig.getValue(configItem.getKey) should be (configItem.getValue)
  }
}
