package coinffeine.peer.config

import coinffeine.common.test.UnitTest

class FileSettingsProviderTest extends UnitTest {

  "File settings provider" should "retrieve user config file" in {
    val settings = FileSettingsProvider("testing-settings")
    settings.userConfigFile().toFile should be ('file)
  }
}
