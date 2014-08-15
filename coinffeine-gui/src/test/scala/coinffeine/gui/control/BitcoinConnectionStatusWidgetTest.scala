package coinffeine.gui.control

import scalafx.application.Platform
import javafx.scene.control.Label
import scalafx.beans.property.ObjectProperty

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.peer.api.event.BitcoinConnectionStatus
import coinffeine.peer.api.event.BitcoinConnectionStatus.{Downloading, NotDownloading}

class BitcoinConnectionStatusWidgetTest extends GuiTest[BitcoinConnectionStatusWidget]
  with Eventually {

  val status = ObjectProperty(BitcoinConnectionStatus(activePeers = 0, NotDownloading))
  override def createRootNode() = new BitcoinConnectionStatusWidget(status)

  "A bitcoin connection status widget" should "start with the provided status" in new Fixture {
    find[Label]("#bitcoin-connection-status").getText should be ("0 bitcoin peers")
  }

  it should "report number of peers we are connected to" in new Fixture {
    Platform.runLater {
      status.set(BitcoinConnectionStatus(activePeers = 5, NotDownloading))
    }
    eventually {
      find[Label]("#bitcoin-connection-status").getText should be ("5 bitcoin peers")
    }
  }

  it should "report the download progress" in new Fixture {
    Platform.runLater {
      status.set(BitcoinConnectionStatus(activePeers = 5, Downloading(100, 64)))
    }
    eventually {
      find[Label]("#bitcoin-connection-status").getText should be (
        "5 bitcoin peers, syncing blockchain (36%)")
    }
  }
}
