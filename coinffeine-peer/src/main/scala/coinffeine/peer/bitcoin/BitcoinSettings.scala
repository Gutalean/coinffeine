package coinffeine.peer.bitcoin

import java.io.File
import scala.concurrent.duration._

case class BitcoinSettings(
  connectionRetryInterval: FiniteDuration,
  walletFile: File,
  blockchainFile: File,
  rebroadcastTimeout: FiniteDuration,
  network: BitcoinSettings.Network,
  spv: Boolean
)

object BitcoinSettings {
  sealed trait Network {
    def name: String
    override def toString = name
  }

  case object IntegrationRegnet extends Network {
    override def name = "integration-regnet"
  }

  case object MainNet extends Network {
    override def name = "mainnet"
  }

  def parseNetwork(name: String): Option[Network] =
    Seq(IntegrationRegnet, MainNet).find(_.toString == name)
}
