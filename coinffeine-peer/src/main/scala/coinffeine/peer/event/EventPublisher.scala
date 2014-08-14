package coinffeine.peer.event

import akka.actor.{Actor}

import coinffeine.peer.api.event.CoinffeineAppEvent

/** An actor able to publish events. */
trait EventPublisher { this: Actor =>

  /** Produce the given event, submitting it to the event stream. */
  def publishEvent(event: CoinffeineAppEvent): Unit = {
    context.system.eventStream.publish(event)
  }
}
