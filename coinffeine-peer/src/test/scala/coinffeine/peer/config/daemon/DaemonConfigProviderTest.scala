package coinffeine.peer.config.daemon

import java.io.File

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

import coinffeine.common.test.UnitTest

class DaemonConfigProviderTest extends UnitTest {

  "A daemon config provider" should "load the configuration file if it exists" in
    withConfigFile("key = value") { configFile =>
      val instance = new DaemonConfigProvider(configFile)
      instance.userConfig.getString("key") shouldBe "value"
    }

  it should "load an empty user configuration otherwise" in
    withConfigFile("key = value") { configFile =>
      configFile.delete()
      val instance = new DaemonConfigProvider(configFile)
      instance.userConfig.entrySet() shouldBe 'empty
    }

  it should "not support updating the user configuration" in
    withConfigFile("key = value") { configFile =>
      val instance = new DaemonConfigProvider(configFile)
      an [UnsupportedOperationException] shouldBe thrownBy {
        instance.saveUserConfig(ConfigFactory.empty())
      }
    }

  def withConfigFile(content: String)(block: File => Unit): Unit = {
    val file = File.createTempFile("config", "properties")
    try {
      FileUtils.write(file, "key = value")
      block(file)
    } finally {
      file.delete()
    }
  }
}
