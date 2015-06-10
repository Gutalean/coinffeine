package coinffeine.common.akka.event

import akka.actor.Actor

trait CoinffeineEventProducer { this: Actor =>

  private val extension = CoinffeineEventBusExtension(context.system)

  protected def publish(topic: String, content: Any) = extension.publish(topic, content)
}
