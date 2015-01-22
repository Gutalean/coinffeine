package coinffeine.peer.bitcoin

import java.io.File
import scala.concurrent.duration._

case class BitcoinSettings(
  connectionRetryInterval: FiniteDuration,
  walletFile: File,
  blockchainFile: File,
  rebroadcastTimeout: FiniteDuration,
  network: BitcoinSettings.Network
)

object BitcoinSettings {
  sealed trait Network {
    def name: String
    override def toString = name
  }

  case object PublicTestnet extends Network {
    override def name = "public-testnet"
  }
  case object IntegrationTestnet extends Network {
    override def name = "integration-testnet"
  }
  case object MainNet extends Network {
    override def name = "mainnet"
  }

  def parseNetwork(name: String): Network =
    Seq(PublicTestnet, IntegrationTestnet, MainNet).find(_.toString == name).getOrElse(
      throw new IllegalArgumentException(s"Unknown bitcoin network: '$name'"))
}
