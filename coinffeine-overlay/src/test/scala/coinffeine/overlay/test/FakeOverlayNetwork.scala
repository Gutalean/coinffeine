package coinffeine.overlay.test

import akka.actor.{Props, ActorSystem}

import coinffeine.overlay.OverlayNetwork

class FakeOverlayNetwork private (serverProps: Props, system: ActorSystem) extends OverlayNetwork {
  override type Config = FakeOverlayNetwork.Config.type
  private val server = system.actorOf(serverProps)
  override def clientProps(config: Config) = ClientActor.props(server)
  def defaultClientProps = clientProps(FakeOverlayNetwork.Config)
}

object FakeOverlayNetwork {

  def apply(messageDroppingRate: Double = 0)(implicit system: ActorSystem): FakeOverlayNetwork = {
    new FakeOverlayNetwork(ServerActor.props(messageDroppingRate), system)
  }

  case object Config
}
