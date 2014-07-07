package com.coinffeine.client.peer

import akka.actor.{ActorLogging, Props, Actor, ActorRef}

import com.coinffeine.client.api.CoinffeineApp

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
    case event: CoinffeineApp.Event =>
      log.debug(s"Delivering event $event to the subscribers")
      for (s <- subscribers) { s.forward(event) }
  }
}

object EventChannelActor {

  /** Retrieve the properties for a new event channel actor. */
  def props(): Props = Props(new EventChannelActor)
}
