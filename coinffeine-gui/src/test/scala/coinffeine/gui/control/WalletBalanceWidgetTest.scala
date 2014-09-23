package coinffeine.gui.control

import java.text.DecimalFormat
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._

class WalletBalanceWidgetTest
    extends GuiTest[WalletBalanceWidget[Bitcoin.type, BitcoinBalance]] with Eventually {

  val balanceProperty = new ObjectProperty[Option[BitcoinBalance]](
    this, "balance", Some(BitcoinBalance(0.BTC)))
  override def createRootNode() = new WalletBalanceWidget(Bitcoin, balanceProperty)

  "A wallet balance widget" should "start with the provided value" in new Fixture {
    find[Label]("#BTC-balance").getText should be (formatNumber(0.0))
    find[HBox]("#balance-widget").getStyleClass should not contain "non-current"
  }

  it should "reflect changes on the balance property" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance(0.85.BTC)))
    }
    eventually {
      find[Label]("#BTC-balance").getText should be (formatNumber(0.85))
      find[HBox]("#balance-widget").getStyleClass should not contain "non-current"
    }
  }

  it should "show up to 8 decimal positions" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance(0.12345678.BTC)))
    }
    eventually {
      find[Label]("#BTC-balance").getText should be (formatNumber(0.12345678))
    }
  }

  it should "represent unavailable balances" in new Fixture {
    Platform.runLater {
      balanceProperty.set(None)
    }
    eventually {
      find[Label]("#BTC-balance").getText should be ("-.--")
      find[HBox]("#balance-widget").getStyleClass should contain ("non-current")
    }
  }

  it should "represent expired balances" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance(10.BTC, hasExpired = true)))
    }
    eventually {
      find[Label]("#BTC-balance").getText should be (formatNumber(10))
      find[HBox]("#balance-widget").getStyleClass should contain ("non-current")
    }
  }

  private def formatNumber(value: Double) = {
    val formatter = new DecimalFormat
    formatter.setMinimumFractionDigits(2)
    formatter.setMaximumFractionDigits(8)
    formatter.format(value)
  }
}
