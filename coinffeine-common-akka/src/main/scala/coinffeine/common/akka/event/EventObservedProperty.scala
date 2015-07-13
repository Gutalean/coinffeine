package coinffeine.common.akka.event

import scala.concurrent.ExecutionContext

import akka.actor.{Actor, ActorSystem, Props}

import coinffeine.common.properties.{MutableProperty, Property}

/** An implementation of [[coinffeine.common.properties.Property]] that observes events in
  * Coinffeine event bus and updates the value managed as property.
  */
class EventObservedProperty[T](topic: String, mapping: PartialFunction[Any, T], initialValue: T)
                              (implicit system: ActorSystem) extends Property[T] {

  private val delegate = new MutableProperty[T](initialValue)

  private class EventObserverActor extends Actor with CoinffeineEventConsumer {

    override def preStart() = { subscribe(topic) }

    override def receive = {
      case event if mapping.isDefinedAt(event) => delegate.set(mapping(event))
    }
  }

  override def get = delegate.get

  override def onChange(handler: OnChangeHandler)(implicit executor: ExecutionContext) =
    delegate.onChange(handler)

  system.actorOf(Props(new EventObserverActor))
}

object EventObservedProperty {

  def apply[T](topic: String, initialValue: T)
              (mapping: PartialFunction[Any, T])
              (implicit system: ActorSystem): EventObservedProperty[T] =
    new EventObservedProperty(topic, mapping, initialValue)(system)
}
