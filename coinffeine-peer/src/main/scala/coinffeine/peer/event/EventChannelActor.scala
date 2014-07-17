package coinffeine.peer.event

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

import coinffeine.peer.CoinffeinePeerActor
import coinffeine.peer.api.event.CoinffeineAppEvent

/** An actor that receives events and forward them to the subscriptors. */
class EventChannelActor extends Actor with ActorLogging {

  private var subscribers: Set[ActorRef] = Set.empty

  override def receive: Receive = {
    case CoinffeinePeerActor.Subscribe =>
      log.info(s"Subscription received from ${sender()}")
      subscribers += sender
    case CoinffeinePeerActor.Unsubscribe =>
      log.info(s"Unsubscription received from ${sender()}")
      subscribers -= sender
    case event: CoinffeineAppEvent =>
      log.debug(s"Delivering event $event to the subscribers")
      for (s <- subscribers) { s.forward(event) }
  }
}

object EventChannelActor {

  /** Retrieve the properties for a new event channel actor. */
  def props(): Props = Props(new EventChannelActor)
}
