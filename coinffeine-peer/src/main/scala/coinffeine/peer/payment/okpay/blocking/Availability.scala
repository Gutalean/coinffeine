package coinffeine.peer.payment.okpay.blocking

/** Whether blocked funds are available or not */
private sealed trait Availability
private case object Available extends Availability
private case object Unavailable extends Availability
