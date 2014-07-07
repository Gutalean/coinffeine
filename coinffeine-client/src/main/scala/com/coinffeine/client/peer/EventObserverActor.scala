package com.coinffeine.client.peer

import akka.actor.{Actor, Props}

import com.coinffeine.client.api.{CoinffeineApp, EventHandler}

class EventObserverActor(handler: EventHandler) extends Actor {

  override def receive: Receive = {
    case event: CoinffeineApp.Event if handler.isDefinedAt(event) =>
      handler(event)
  }
}

object EventObserverActor {

  def props(handler: EventHandler): Props = Props(new EventObserverActor(handler))
}
