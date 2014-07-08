package com.coinffeine.client.event

import akka.actor.{ActorRef, Actor}

import com.coinffeine.client.api.CoinffeineApp

/** An actor able to produce events. */
abstract class EventProducer(channel: ActorRef) {

  /** Produce the given event, submitting it to the event channel. */
  def produceEvent(event: CoinffeineApp.Event)(implicit sender: ActorRef = Actor.noSender): Unit = {
    channel ! event
  }
}
