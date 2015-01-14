package coinffeine.overlay.test

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{Props, ActorSystem}

import coinffeine.overlay.OverlayNetwork

class FakeOverlayNetwork private (serverProps: Props, system: ActorSystem) extends OverlayNetwork {
  private val server = system.actorOf(serverProps)
  override val clientProps = ClientActor.props(server)
}

object FakeOverlayNetwork {

  trait DelayDistribution {
    /** Next delay, None for infinite delay */
    def nextDelay(): Option[FiniteDuration]
  }

  object NoDelay extends DelayDistribution {
    override val nextDelay = Some(0.seconds)
  }

  object NeverHappens extends DelayDistribution {
    override val nextDelay = None
  }

  class ExponentialDelay(mean: FiniteDuration) extends DelayDistribution {
    private val generator = new Random()
    override def nextDelay() = Some((-Math.log(generator.nextDouble()) * mean.toMillis).millis)
  }

  def apply(messageDroppingRate: Double = 0,
            connectionFailureRate: Double = 0,
            delayDistribution: DelayDistribution = NoDelay,
            disconnectionDistribution: DelayDistribution = NeverHappens)
           (implicit system: ActorSystem): FakeOverlayNetwork = {
    val serverProps = ServerActor.props(
      messageDroppingRate, connectionFailureRate, delayDistribution, disconnectionDistribution)
    new FakeOverlayNetwork(serverProps, system)
  }

  case object Config
}
