package coinffeine.common.akka.event

import scala.concurrent.ExecutionContext

import akka.actor.{Actor, ActorSystem, Props}

import coinffeine.common.properties.{MutablePropertyMap, PropertyMap}

/** An implementation of [[coinffeine.common.properties.PropertyMap]] that observes events in
  * Coinffeine event bus and updates the map contents appropriately.
  */
class EventObservedPropertyMap[K, V](
    topic: String,
    mapping: PartialFunction[Any, EventObservedPropertyMap.Command[K, V]])
    (implicit system: ActorSystem) extends PropertyMap[K, V] {

  import EventObservedPropertyMap._

  private val delegate = new MutablePropertyMap[K, V]()

  override def get(key: K) = delegate.get(key)

  override def onChange(handler: OnEntryChangeHandler)
                       (implicit executor: ExecutionContext) = delegate.onChange(handler)(executor)

  override def content = delegate.content

  private class EventObserverActor extends Actor with CoinffeineEventConsumer {

    override def preStart() = { subscribe(topic) }

    override def receive = {
      case event if mapping.isDefinedAt(event) => mapping(event) match {
        case Put(k, v) => delegate.set(k, v)
        case Remove(k) => delegate.remove(k)
      }
    }
  }

  system.actorOf(Props(new EventObserverActor))
}

object EventObservedPropertyMap {

  sealed trait Command[K, V]
  case class Put[K, V](key: K, value: V) extends Command[K, V]
  case class Remove[K, V](key: K) extends Command[K, V]

  def apply[K, V](topic: String)
                 (mapping: PartialFunction[Any, Command[K, V]])
                 (implicit system: ActorSystem): EventObservedPropertyMap[K, V] =
    new EventObservedPropertyMap(topic, mapping)(system)
}
