package coinffeine.peer.api.event

import akka.actor.ActorSystem
import akka.testkit.TestProbe

/** Thin wrapper over TestProbe to assert on [[CoinffeineAppEvent]]s produced */
class EventChannelProbe(actorSystem: ActorSystem) extends TestProbe(actorSystem: ActorSystem) {
  system.eventStream.subscribe(ref, classOf[CoinffeineAppEvent])
}

object EventChannelProbe {
  def apply()(implicit system: ActorSystem) = new EventChannelProbe(system)
}
