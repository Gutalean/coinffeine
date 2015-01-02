package coinffeine.overlay.test

import akka.actor.ActorSystem

import coinffeine.overlay.OverlayNetwork

class FakeOverlayNetwork private (system: ActorSystem) extends OverlayNetwork {
  override type Config = FakeOverlayNetwork.Config
  private val server = system.actorOf(ServerActor.props)
  override def clientProps(config: Config) = ClientActor.props(server)
  def defaultClientProps = clientProps(FakeOverlayNetwork.Config())
}

object FakeOverlayNetwork {

  def apply()(implicit system: ActorSystem): FakeOverlayNetwork = {
    new FakeOverlayNetwork(system)
  }

  case class Config()
}
