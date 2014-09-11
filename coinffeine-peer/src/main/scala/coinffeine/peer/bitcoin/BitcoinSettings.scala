package coinffeine.peer.bitcoin

import java.io.File
import scala.concurrent.duration._

case class BitcoinSettings(
  connectionRetryInterval: FiniteDuration,
  walletFile: File
)
