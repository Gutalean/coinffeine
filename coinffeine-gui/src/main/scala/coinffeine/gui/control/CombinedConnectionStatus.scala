package coinffeine.gui.control

import coinffeine.model.event.BitcoinConnectionStatus.{Downloading, NotDownloading}
import coinffeine.model.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus}

/** Combines Coinffeine and Bitcoin Connection status information */
case class CombinedConnectionStatus(
    coinffeineStatus: CoinffeineConnectionStatus,
    bitcoinStatus: BitcoinConnectionStatus) {
  import CombinedConnectionStatus._

  val color: StatusColor =
    if (!coinffeineStatus.connected || !bitcoinStatus.connected) Red
    else bitcoinStatus.blockchainStatus match {
      case NotDownloading => Green
      case _: Downloading => Yellow
    }

  val description: String = "%s, %s%s".format(
    formatPeerCount(coinffeineStatus.activePeers, "coinffeine"),
    formatPeerCount(bitcoinStatus.activePeers, "bitcoin"),
    formatBlockchainSyncing
  )

  private def formatPeerCount(count: Int, name: String): String = {
    val pluralS = if (count == 1) "" else "s"
    s"$count $name peer$pluralS"
  }

  private def formatBlockchainSyncing: String = {
    bitcoinStatus.blockchainStatus match {
      case NotDownloading => ""
      case download: Downloading =>
        val percent = (download.progress * 100).toInt
        s", syncing blockchain ($percent%)"
    }
  }
}

object CombinedConnectionStatus {
  sealed trait StatusColor
  case object Red extends StatusColor
  case object Yellow extends StatusColor
  case object Green extends StatusColor
}
