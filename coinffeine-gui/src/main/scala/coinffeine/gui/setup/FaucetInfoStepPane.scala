package coinffeine.gui.setup

import java.net.URI
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.{StackPane, VBox}

import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.util.Browser
import coinffeine.gui.wizard.StepPane

private[setup] class FaucetInfoStepPane(address: String)
  extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Number2

  private val title = new Label("Obtain Technical Preview credentials") { styleClass += "title" }

  private val par1 = new Label {
    text = "This is a technical preview. We use a private Bitcoin testnet " +
      "and a mocked OKPay service to allow you testing Coinffeine without risking money."
  }

  private val par2 = new Label {
    text = "Please, use our faucet site to obtain your testing credentials."
  }

  private val gotoFaucetLine = new Button("Go to Faucet Site") {
    handleEvent(ActionEvent.Action) { () => openFaucet()}
  }

  children = new VBox {
    styleClass += "faucet-pane"
    children = Seq(title, par1, par2, new SupportWidget("setup-faucet"), gotoFaucetLine)
  }

  private def openFaucet(): Unit = {
    val location = URI.create(s"${FaucetInfoStepPane.FaucetUrl}index.html?address=$address")
    Browser.default.browse(location)
  }
}

private[setup] object FaucetInfoStepPane {
  val FaucetUrl = new URI("http://faucet.coinffeine.com/")
}
