package coinffeine.peer.config.user

import coinffeine.peer.config.ConfigComponent

trait UserFileConfigComponent extends ConfigComponent {
  lazy val configProvider = UserFileConfigProvider()
}
