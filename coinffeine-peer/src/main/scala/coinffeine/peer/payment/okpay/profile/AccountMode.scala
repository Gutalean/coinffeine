package coinffeine.peer.payment.okpay.profile

sealed trait AccountMode
object AccountMode {
  case object Business extends AccountMode
  case object Client extends AccountMode
}
