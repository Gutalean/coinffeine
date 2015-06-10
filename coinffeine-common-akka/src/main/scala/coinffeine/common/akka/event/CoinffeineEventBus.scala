package coinffeine.common.akka.event

import scala.collection.concurrent.TrieMap

import akka.actor.ActorRef
import akka.event.{ActorEventBus, LookupClassification}

/** A topic-oriented event bus designed to send internal events in Coinffeine actor system.
  *
  * In order to minimize the number of interactions among actors and their complexity an
  * auxiliary infrastructure based on event bus is provided to Coinffeine's actor system.
  *
  * The Coinffeine event bus is a topic-oriented channel with event retention. Each significant
  * event has an associated topic, identified by [[String]] type. Event producers send their
  * data (represented by [[Any]] type) associated with a topic, and event consumers subscribe
  * their interest to specific topics. When a event is notified through the bus with an specific
  * topic, every actor subscribed to that topic will receive the event as a regular message.
  * Thanks to the retention feature, every time an actor subscribes to a topic it receives the
  * last event sent to that topic (if any). This is especially useful to avoid _I've subscribed
  * late and my event is lost_ situations.
  *
  */
class CoinffeineEventBus extends ActorEventBus with LookupClassification {

  import CoinffeineEventBus._

  override type Classifier = String
  override type Event = Envelope

  private val retained = TrieMap.empty[Classifier, Any]

  override protected def mapSize() = InitialMapSize

  override protected def publish(event: Event, subscriber: ActorRef) = {
    if (subscriber.isTerminated) { unsubscribe(subscriber) }
    else { subscriber ! event.content }
  }

  override protected def classify(event: Event) = event.topic

  override def subscribe(subscriber: ActorRef, topic: String) = {
    val subscribed = super.subscribe(subscriber, topic)
    if (subscribed) {
      retained.get(topic).foreach(event => subscriber ! event)
    }
    subscribed
  }

  override def publish(event: Envelope) = {
    retained.put(event.topic, event.content)
    super.publish(event)
  }

  def publish(topic: String, content: Any): Unit = publish(Envelope(topic, content))
}

object CoinffeineEventBus {

  private val InitialMapSize = 128

  case class Envelope(topic: String, content: Any)
}
