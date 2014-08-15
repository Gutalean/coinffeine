package coinffeine.gui.control

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.scene.control.Label
import coinffeine.gui.util.ScalafxImplicits._

import coinffeine.peer.api.event.BitcoinConnectionStatus
import coinffeine.peer.api.event.BitcoinConnectionStatus.{NotDownloading, Downloading}

class BitcoinConnectionStatusWidget(status: ReadOnlyObjectProperty[BitcoinConnectionStatus])
  extends Label {

  val statusColor = ObjectProperty[StatusDisc.Status](StatusDisc.Red)
  statusColor.bind(status.delegate.map[StatusDisc.Status] {
    case BitcoinConnectionStatus(peers, _) if peers < 1 => StatusDisc.Red
    case BitcoinConnectionStatus(_, Downloading(_, _)) => StatusDisc.Yellow
    case _ => StatusDisc.Green
  })

  id = "bitcoin-connection-status"
  graphic = new StatusDisc(statusColor)
  text <== status.delegate.map(statusMessage)

  private def statusMessage(status: BitcoinConnectionStatus): String = {
    val downloadMessage = status.blockchainStatus match {
      case NotDownloading => ""
      case download: Downloading =>
        val percent = (download.progress * 100).toInt
        s", syncing blockchain ($percent%)"
    }
    s"${status.activePeers} bitcoin peers$downloadMessage"
  }
}
