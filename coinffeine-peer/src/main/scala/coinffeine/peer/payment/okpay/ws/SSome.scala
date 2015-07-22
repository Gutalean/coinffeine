package coinffeine.peer.payment.okpay.ws

/** Helpers for handling highly-nested options from generated code */
object SSome {

  def apply[A](value: A): Some[Some[A]] = Some(Some(value))

  def unapply[A](expression: Option[Option[A]]): Option[A] = expression match {
    case Some(Some(value)) => Some(value)
    case _ => None
  }
}
