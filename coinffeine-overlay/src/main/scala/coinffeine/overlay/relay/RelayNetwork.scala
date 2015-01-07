package coinffeine.overlay.relay

import akka.actor.{ActorSystem, Props}

import coinffeine.overlay.OverlayNetwork

class RelayNetwork private (system: ActorSystem) extends OverlayNetwork {
  override type Config = ClientConfig

  override def clientProps(config: Config): Props = ???
}
