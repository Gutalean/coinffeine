package coinffeine.peer.event

import akka.actor.{Actor, Props}

import coinffeine.model.event.CoinffeineAppEvent
import coinffeine.peer.api.EventHandler

class EventObserverActor(handler: EventHandler) extends Actor {

  override def receive: Receive = {
    case event: CoinffeineAppEvent if handler.isDefinedAt(event) =>
      handler(event)
  }
}

object EventObserverActor {
  def props(handler: EventHandler): Props = Props(new EventObserverActor(handler))
}
