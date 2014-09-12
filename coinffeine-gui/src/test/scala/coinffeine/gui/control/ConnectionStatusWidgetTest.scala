package coinffeine.gui.control

import javafx.scene.control.Label
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.model.bitcoin.BlockchainStatus

class ConnectionStatusWidgetTest extends GuiTest[ConnectionStatusWidget]
  with Eventually {

  val status = ObjectProperty(ConnectionStatus(
    ConnectionStatus.Coinffeine(0),
    ConnectionStatus.Bitcoin(activePeers = 0, BlockchainStatus.NotDownloading)
  ))
  override def createRootNode() = new ConnectionStatusWidget(status)

  "A bitcoin connection status widget" should "start with the provided status" in new Fixture {
    find[Label]("#connection-status").getText should be ("0 coinffeine peers, 0 bitcoin peers")
  }

  it should "respond to status updates" in new Fixture {
    Platform.runLater {
      status.set(ConnectionStatus(
        ConnectionStatus.Coinffeine(5),
        ConnectionStatus.Bitcoin(activePeers = 10, BlockchainStatus.NotDownloading)
      ))
    }
    eventually {
      find[Label]("#connection-status").getText should be ("5 coinffeine peers, 10 bitcoin peers")
    }
  }
}
