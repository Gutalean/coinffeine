package coinffeine.peer.api.event

/** A coinffeine app event that is susceptible to be notified to the UI. */
trait NotifiableCoinffeineAppEvent extends CoinffeineAppEvent {

  def eventType: NotifiableCoinffeineAppEvent.Type
  def summary: String
  def description: String
}

object NotifiableCoinffeineAppEvent {

  sealed trait Type
  case object Info extends Type
  case object Warning extends Type
  case object Error extends Type
  case object Success extends Type
}
