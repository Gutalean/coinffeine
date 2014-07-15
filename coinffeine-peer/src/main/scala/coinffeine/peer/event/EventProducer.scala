package coinffeine.peer.event

import akka.actor.{Actor, ActorRef}

import coinffeine.peer.api.CoinffeineApp

/** An actor able to produce events. */
abstract class EventProducer(channel: ActorRef) {

  /** Produce the given event, submitting it to the event channel. */
  def produceEvent(event: CoinffeineApp.Event)(implicit sender: ActorRef = Actor.noSender): Unit = {
    channel ! event
  }
}
