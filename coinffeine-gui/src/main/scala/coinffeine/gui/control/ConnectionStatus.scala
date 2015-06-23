package coinffeine.gui.control

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import coinffeine.model.bitcoin.BlockchainStatus
import coinffeine.model.network.PeerId

/** Combines Coinffeine and Bitcoin Connection status information */
case class ConnectionStatus(
    coinffeine: ConnectionStatus.Coinffeine,
    bitcoin: ConnectionStatus.Bitcoin) {

  val description: String = "%s, %s%s".format(
    formatPeerCount(coinffeine.activePeers, "coinffeine"),
    formatPeerCount(bitcoin.activePeers, "bitcoin"),
    formatDetails
  )

  val connected = coinffeine.connected && bitcoin.connected

  private def formatPeerCount(count: Int, name: String): String = {
    val pluralS = if (count == 1) "" else "s"
    s"$count $name peer$pluralS"
  }

  private def formatDetails: String = {
    bitcoin.blockchainStatus match {
      case BlockchainStatus.NotDownloading(None) => ""
      case BlockchainStatus.NotDownloading(Some(lastBlock)) =>
        val height = lastBlock.height
        val on = DateTimeFormat.shortDateTime().print(lastBlock.date)
        s", last block $height ($on)"
      case download: BlockchainStatus.Downloading =>
        val percent = (download.progress * 100).toInt
        s", syncing blockchain ($percent%)"
    }
  }
}

object ConnectionStatus {

  case class Coinffeine(activePeers: Int = 0, brokerId: Option[PeerId] = None) {
    def connected: Boolean = activePeers > 0 && brokerId.isDefined
  }

  case class Bitcoin(activePeers: Int, blockchainStatus: BlockchainStatus) {
    def connected: Boolean = activePeers > 0
  }
}
