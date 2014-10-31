package coinffeine.peer.bitcoin

import java.io.File
import scala.concurrent.duration._

case class BitcoinSettings(
  connectionRetryInterval: FiniteDuration,
  walletFile: File,
  rebroadcastTimeout: FiniteDuration,
  network: BitcoinSettings.Network
)

object BitcoinSettings {
  sealed trait Network
  case object PublicTestnet extends Network {
    override def toString = "public-testnet"
  }
  case object IntegrationTestnet extends Network {
    override def toString = "integration-testnet"
  }
  case object MainNet extends Network {
    override def toString = "mainnet"
  }

  def parseNetwork(name: String): Network =
    Seq(PublicTestnet, IntegrationTestnet, MainNet).find(_.toString == name).getOrElse(
      throw new IllegalArgumentException(s"Unknown bitcoin network: '$name'"))
}
