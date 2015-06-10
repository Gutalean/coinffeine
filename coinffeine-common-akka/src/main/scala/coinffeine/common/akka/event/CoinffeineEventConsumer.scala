package coinffeine.common.akka.event

import akka.actor.Actor

trait CoinffeineEventConsumer { this: Actor =>

  private val extension = CoinffeineEventBusExtension(context.system)

  protected def subscribe(topic: String) = extension.subscribe(self, topic)
}
