package coinffeine.model.event

import org.joda.time.DateTime

/** A significant event occurred in the Coinffeine App. */
trait CoinffeineAppEvent {

  val date = DateTime.now()
}
