package coinffeine.peer.payment.okpay

case class OkPayApiCredentials(walletId: String, seedToken: String)

object OkPayApiCredentials {
  val empty = OkPayApiCredentials("", "")
}
