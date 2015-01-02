package coinffeine.overlay.test

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Props, ActorSystem}

import coinffeine.overlay.OverlayNetwork

class FakeOverlayNetwork private (serverProps: Props, system: ActorSystem) extends OverlayNetwork {
  override type Config = FakeOverlayNetwork.Config.type
  private val server = system.actorOf(serverProps)
  override def clientProps(config: Config) = ClientActor.props(server)
  def defaultClientProps = clientProps(FakeOverlayNetwork.Config)
}

object FakeOverlayNetwork {

  trait DelayDistribution {
    def nextDelay(): FiniteDuration
  }

  object NoDelay extends DelayDistribution {
    override val nextDelay = 0.seconds
  }

  class ExponentialDelay(mean: FiniteDuration) extends DelayDistribution {
    private val generator = new Random()
    override def nextDelay() = (-Math.log(generator.nextDouble()) * mean.toMillis).millis
  }

  def apply(messageDroppingRate: Double = 0,
            connectionFailureRate: Double = 0,
            delayDistribution: DelayDistribution = NoDelay)
           (implicit system: ActorSystem): FakeOverlayNetwork = {
    val serverProps = ServerActor.props(messageDroppingRate, connectionFailureRate, delayDistribution)
    new FakeOverlayNetwork(serverProps, system)
  }

  case object Config
}
