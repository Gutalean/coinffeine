package coinffeine.peer.config.user

import java.io.File

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

import coinffeine.common.test.{TempDir, UnitTest}

class UserFileConfigStoreTest extends UnitTest {

  "User file config store" must "return empty config if file not found" in {
    TempDir.withTempDir { dataPath =>
      val store = new UserFileConfigStore(dataPath)
      store.readConfig() shouldBe ConfigFactory.empty()
    }
  }

  it must "load config if it exists" in {
    TempDir.withTempDir { dataPath =>
      val userFile = new File(dataPath, "foobar.cfg")
      FileUtils.write(userFile, "foobar = 42")

      val store = new UserFileConfigStore(dataPath, "foobar.cfg")
      store.readConfig() shouldBe ConfigFactory.parseString("foobar = 42")
    }
  }

  it must "write config" in {
    TempDir.withTempDir { dataPath =>
      val expectedConfig = ConfigFactory.parseString("foobar = 42")
      val store = new UserFileConfigStore(dataPath, "foobar.cfg")
      store.writeConfig(expectedConfig)

      val userFile = new File(dataPath, "foobar.cfg")
      ConfigFactory.parseFile(userFile) shouldBe expectedConfig
    }
  }
}
