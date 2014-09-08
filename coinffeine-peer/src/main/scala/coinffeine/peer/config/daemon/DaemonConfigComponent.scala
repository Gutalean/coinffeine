package coinffeine.peer.config.daemon

import java.io.File

import coinffeine.peer.config.ConfigComponent

trait DaemonConfigComponent extends ConfigComponent {
  lazy val configProvider = new DaemonConfigProvider(DaemonConfigComponent.ConfigFile)
}

object DaemonConfigComponent {
  val ConfigFile = new File("/etc/coinffeine.properties")
}
