package coinffeine.peer.config

import com.typesafe.config.ConfigFactory

trait FileConfigComponent extends ConfigComponent {
  override lazy val config = ConfigFactory.load()
}
