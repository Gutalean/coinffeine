package coinffeine.peer.config.user

import coinffeine.peer.config.{ConfigComponent, ConfigProvider}

trait UserFileConfigComponent extends ConfigComponent {
  lazy val configProvider: ConfigProvider =
    new UserFileConfigProvider(LocalAppDataDir().toAbsolutePath.toFile)
}
