package coinffeine.peer.bitcoin

import scala.concurrent.duration._

case class BitcoinSettings(
  connectionRetryInterval: FiniteDuration
)
