package coinffeine.gui.control

import java.text.DecimalFormat
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import org.loadui.testfx.exceptions.NoNodesVisibleException
import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.gui.control.wallet.WalletBalanceWidget
import coinffeine.model.currency._

class WalletBalanceWidgetTest
    extends GuiTest[WalletBalanceWidget[Bitcoin.type, BitcoinBalance]] with Eventually {

  val balanceProperty = new ObjectProperty[Option[BitcoinBalance]](
    this, "balance", Some(BitcoinBalance.singleOutput(0.BTC)))
  override def createRootNode() =
    new WalletBalanceWidget(Bitcoin, balanceProperty) with NoPopUpContent

  "A wallet balance widget" should "start with the provided value" in new Fixture {
    findBalanceLabel().getText shouldBe formatNumber(0.0)
    expectNoWarnImageVisible()
  }

  it should "reflect changes on the balance property" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance.singleOutput(0.85.BTC)))
    }
    eventually {
      findBalanceLabel().getText shouldBe formatNumber(0.85)
      expectNoWarnImageVisible()
    }
  }

  it should "show up to 8 decimal positions" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance.singleOutput(0.12345678.BTC)))
    }
    eventually {
      findBalanceLabel().getText shouldBe formatNumber(0.12345678)
    }
  }

  it should "represent unavailable balances" in new Fixture {
    Platform.runLater {
      balanceProperty.set(None)
    }
    eventually {
      findBalanceLabel().getText shouldBe "-.--"
      findWarnImage() shouldBe 'visible
    }
  }

  it should "represent expired balances" in new Fixture {
    Platform.runLater {
      balanceProperty.set(Some(BitcoinBalance.singleOutput(10.BTC).copy(hasExpired = true)))
    }
    eventually {
      findBalanceLabel().getText shouldBe formatNumber(10)
      findWarnImage() shouldBe 'visible
    }
  }

  private def findBalanceLabel(): Label = find[Label]("#BTC-balance Label")

  private def findWarnImage(): ImageView = find[ImageView](".balance-warning")

  private def expectNoWarnImageVisible(): Unit = {
    a[NoNodesVisibleException] shouldBe thrownBy {
      findWarnImage()
    }
  }

  private def formatNumber(value: Double) = {
    val formatter = new DecimalFormat
    formatter.setMinimumFractionDigits(2)
    formatter.setMaximumFractionDigits(8)
    formatter.format(value)
  }
}
