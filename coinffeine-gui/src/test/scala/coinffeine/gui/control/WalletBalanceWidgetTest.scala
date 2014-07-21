package coinffeine.gui.control

import javafx.scene.control.Label
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._

class WalletBalanceWidgetTest extends GuiTest[WalletBalanceWidget[Bitcoin.type]] with Eventually {

  val balanceProperty = new ObjectProperty[Option[BitcoinAmount]](this, "balance", Some(0.BTC))
  override def createRootNode() = new WalletBalanceWidget(Bitcoin, balanceProperty)

  "A wallet balance widget" should "start with the provided value" in new Fixture {
    find[Label]("#BTC-balance").getText should be ("0.00")
  }

  it should "reflect changes on the balance property" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(0.85.BTC))
    }
    eventually {
      find[Label]("#BTC-balance").getText should be ("0.85")
    }
  }

  it should "show up to 8 decimal positions" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(0.12345678.BTC))
    }
    eventually {
      find[Label]("#BTC-balance").getText should be ("0.12345678")
    }
  }

  it should "represent unavailable balances" in new Fixture {
    Platform.runLater {
      balanceProperty.set(None)
    }
    eventually {
      find[Label]("#BTC-balance").getText should be ("-.--")
    }
  }
}
