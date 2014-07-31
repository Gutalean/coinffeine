package coinffeine.peer.api.event

import org.joda.time.DateTime

/** A significant event occurred in the Coinffeine App. */
trait CoinffeineAppEvent {

  val date = DateTime.now()
}
