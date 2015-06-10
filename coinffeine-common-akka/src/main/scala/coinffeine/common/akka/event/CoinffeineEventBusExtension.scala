package coinffeine.common.akka.event

import akka.actor._

/** An Akka extension that provides a bus for Coinffeine app internal events. */
class CoinffeineEventBusExtension extends Extension {

  private val bus = new CoinffeineEventBus

  def subscribe(subscriber: ActorRef, topic: String) = bus.subscribe(subscriber, topic)
  def publish(topic: String, content: Any) = bus.publish(topic, content)
}

object CoinffeineEventBusExtension extends ExtensionId[CoinffeineEventBusExtension]
    with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem) = new CoinffeineEventBusExtension

  override def lookup() = CoinffeineEventBusExtension
}
