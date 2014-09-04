package coinffeine.peer.bitcoin

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.config.Config

case class BitcoinSettings(
  walletPrivateKey: String,
  connectionRetryInterval: FiniteDuration
)

object BitcoinSettings {

  def apply(config: Config): BitcoinSettings = BitcoinSettings(
    walletPrivateKey = config.getString("coinffeine.wallet.key"),
    connectionRetryInterval =
      config.getDuration("coinffeine.bitcoin.connectionRetryInterval", TimeUnit.SECONDS).seconds
  )
}
