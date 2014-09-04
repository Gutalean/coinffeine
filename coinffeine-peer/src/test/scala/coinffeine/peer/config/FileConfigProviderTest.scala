package coinffeine.peer.config

import coinffeine.common.test.UnitTest

class FileConfigProviderTest extends UnitTest {

  "File settings provider" should "retrieve user config file" in {
    val settings = FileConfigProvider("testing-settings")
    settings.userConfigFile().toFile should be ('file)
  }
}
