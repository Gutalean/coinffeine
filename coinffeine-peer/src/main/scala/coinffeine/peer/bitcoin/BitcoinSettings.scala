package coinffeine.peer.bitcoin

import scala.concurrent.duration._

case class BitcoinSettings(
  walletPrivateKey: String,
  connectionRetryInterval: FiniteDuration
)
