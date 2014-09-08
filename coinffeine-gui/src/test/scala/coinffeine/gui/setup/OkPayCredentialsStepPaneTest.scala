package coinffeine.gui.setup

import scalafx.beans.property.ObjectProperty

import org.scalatest.OptionValues

import coinffeine.gui.GuiTest
import coinffeine.peer.payment.okpay.OkPayCredentials

class OkPayCredentialsStepPaneTest extends GuiTest[OkPayCredentialsStepPane] with OptionValues {

  val configProperty = new ObjectProperty(this, "config", SetupConfig(
    password = None,
    okPayWalletAccess = None,
    okPayCredentials = None
  ))
  override def createRootNode() = {
    val pane = new OkPayCredentialsStepPane()
    pane.bindTo(configProperty)
    pane
  }

  "The OKPay credentials step pane" should "gather email and password information" in new Fixture {
    click("#email").`type`("email")
    click("#password").`type`("password")
    configProperty.value.okPayCredentials.value shouldBe OkPayCredentials("email", "password")
  }
}
