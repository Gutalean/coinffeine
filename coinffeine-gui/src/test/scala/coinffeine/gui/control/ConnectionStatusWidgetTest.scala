package coinffeine.gui.control

import javafx.scene.control.Label
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.peer.api.event.BitcoinConnectionStatus.NotDownloading
import coinffeine.peer.api.event.{BitcoinConnectionStatus, CoinffeineConnectionStatus}

class ConnectionStatusWidgetTest extends GuiTest[ConnectionStatusWidget]
  with Eventually {

  val status = ObjectProperty(CombinedConnectionStatus(
    CoinffeineConnectionStatus(0),
    BitcoinConnectionStatus(activePeers = 0, NotDownloading)
  ))
  override def createRootNode() = new ConnectionStatusWidget(status)

  "A bitcoin connection status widget" should "start with the provided status" in new Fixture {
    find[Label]("#connection-status").getText should be ("0 coinffeine peers, 0 bitcoin peers")
  }

  it should "respond to status updates" in new Fixture {
    Platform.runLater {
      status.set(CombinedConnectionStatus(
        CoinffeineConnectionStatus(5),
        BitcoinConnectionStatus(activePeers = 10, NotDownloading)
      ))
    }
    eventually {
      find[Label]("#connection-status").getText should be ("5 coinffeine peers, 10 bitcoin peers")
    }
  }
}
