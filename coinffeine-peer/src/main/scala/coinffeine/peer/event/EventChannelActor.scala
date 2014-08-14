package coinffeine.peer.event

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api.event.CoinffeineAppEvent

/** An actor that receives events and forward them to the subscriptors. */
class EventChannelActor extends Actor with ActorLogging {

  override def receive: Receive = {
    case CoinffeinePeerActor.Subscribe =>
      log.info(s"Subscription received from ${sender()}")
      context.system.eventStream.subscribe(sender(), classOf[CoinffeineAppEvent])
    case CoinffeinePeerActor.Unsubscribe =>
      log.info(s"Unsubscription received from ${sender()}")
      context.system.eventStream.unsubscribe(sender(), classOf[CoinffeineAppEvent])
    case event: CoinffeineAppEvent =>
      log.debug(s"Delivering event $event to the subscribers")
      context.system.eventStream.publish(event)
  }
}

object EventChannelActor {

  /** Retrieve the properties for a new event channel actor. */
  def props(): Props = Props(new EventChannelActor)
}
