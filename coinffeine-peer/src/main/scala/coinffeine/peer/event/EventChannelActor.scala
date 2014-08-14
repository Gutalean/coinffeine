package coinffeine.peer.event

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api.event.CoinffeineAppEvent

/** An actor that receives events and forward them to the subscriptors. */
class EventChannelActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case event: CoinffeineAppEvent =>
      log.debug(s"Delivering event $event to the subscribers")
      context.system.eventStream.publish(event)
  }
}

object EventChannelActor {

  /** Retrieve the properties for a new event channel actor. */
  def props(): Props = Props(new EventChannelActor)
}
