package coinffeine.gui.setup

import java.net.URI
import scalafx.beans.property.ObjectProperty
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout.{HBox, Priority, StackPane, VBox}

import coinffeine.gui.scene.styles._
import coinffeine.gui.util.Browser
import coinffeine.gui.wizard.StepPane

private[setup] class FaucetInfoStepPane(address: String) extends StackPane with StepPane[SetupConfig] {

  private val addressTextField = new TextField {
    hgrow = Priority.Always
    id = "address"
    text = address
    editable = false
  }

  content = new VBox with BoxStyles.Paragraphs {
    content = Seq(
      new Label("Obtain Technical Preview credentials") with TextStyles.H2,
      new Label with TextStyles.TextWrapped {
        text = "This is Coinffeine Technical Preview. We use a private Bitcoin testnet " +
          "and a mocked OKPay service to avoid money lost due to application failures."
      },
      new Label with TextStyles.TextWrapped {
        text = "Please copy the Bitcoin address below to the clipboard and use it in our " +
          "Faucet site to obtain your credentials."
      },
      new HBox with BoxStyles.TextFieldWithButton {
        content = Seq(
          addressTextField,
          new Button("Copy to clipboard") {
            handleEvent(ActionEvent.Action) { () => copyAddressToClipboard() }
          }
        )
      },
      new HBox with BoxStyles.ButtonRow {
        content = new Button("Go to Faucet Site") {
          handleEvent(ActionEvent.Action) { () => openFaucet()}
        }
      }
    )
  }

  override def bindTo(data: ObjectProperty[SetupConfig]): Unit = {
    canContinue.value = true
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
