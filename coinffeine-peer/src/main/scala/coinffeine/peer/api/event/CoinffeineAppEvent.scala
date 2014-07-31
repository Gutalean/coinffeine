package coinffeine.peer.api.event

import org.joda.time.DateTime

/** A significant event occurred in the Coinffeine App. */
trait CoinffeineAppEvent {

  val date = DateTime.now()

  def eventType: CoinffeineAppEvent.Type
  def summary: String
}

object CoinffeineAppEvent {

  sealed trait Type
  case object Info extends Type
  case object Warning extends Type
  case object Error extends Type
  case object Success extends Type
}
