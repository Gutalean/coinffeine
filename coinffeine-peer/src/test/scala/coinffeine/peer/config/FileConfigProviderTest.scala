package coinffeine.peer.config

import com.typesafe.config.ConfigFactory
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
}
