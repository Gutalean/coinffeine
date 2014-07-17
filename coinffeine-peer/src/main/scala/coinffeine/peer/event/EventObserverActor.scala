package coinffeine.peer.event

import akka.actor.{Actor, Props}

import coinffeine.peer.api.EventHandler
import coinffeine.peer.api.event.CoinffeineAppEvent

class EventObserverActor(handler: EventHandler) extends Actor {

  override def receive: Receive = {
    case event: CoinffeineAppEvent if handler.isDefinedAt(event) =>
      handler(event)
  }
}

object EventObserverActor {
  def props(handler: EventHandler): Props = Props(new EventObserverActor(handler))
}
