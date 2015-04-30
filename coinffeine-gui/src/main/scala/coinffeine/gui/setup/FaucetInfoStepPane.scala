package coinffeine.gui.setup

import java.net.URI
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout.{HBox, Priority, StackPane, VBox}

import coinffeine.gui.control.{GlyphIcon, SupportWidget}
import coinffeine.gui.util.Browser
import coinffeine.gui.wizard.StepPane

private[setup] class FaucetInfoStepPane(address: String)
  extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Number2

  private val addressTextField = new TextField {
    hgrow = Priority.Always
    id = "address"
    text = address
    editable = false
  }

  private val title = new Label("Obtain Technical Preview credentials") { styleClass += "title" }

  private val par1 = new Label {
    text = "This is a technical preview. We use a private Bitcoin testnet " +
      "and a mocked OKPay service to allow you testing Coinffeine without risking money."
  }

  private val par2 = new Label {
    text = "Please, copy the Bitcoin address below to the clipboard and use it in our " +
      "faucet site to obtain your credentials."
  }

  private val addressLine = new HBox {
    styleClass += "address"
    content = Seq(
      addressTextField,
      new Button("Copy to clipboard") {
        handleEvent(ActionEvent.Action) { () => copyAddressToClipboard() }
      }
    )
  }

  private val gotoFaucetLine = new Button("Go to Faucet Site") {
    handleEvent(ActionEvent.Action) { () => openFaucet()}
  }

  content = new VBox {
    styleClass += "faucet-pane"
    content = Seq(title, par1, par2, new SupportWidget("setup-faucet"), addressLine, gotoFaucetLine)
  }

  private def openFaucet(): Unit = {
    Browser.default.browse(FaucetInfoStepPane.FaucetUrl)
  }

  private def copyAddressToClipboard(): Unit = {
    val content = new ClipboardContent()
    content.putString(addressTextField.text.value)
    Clipboard.systemClipboard.setContent(content)
  }
}

private[setup] object FaucetInfoStepPane {
  val FaucetUrl = new URI("http://faucet.coinffeine.com/")
}
