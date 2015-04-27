package coinffeine.gui.application.wallet

import scala.util.Try
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout._
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{SupportWidget, CurrencyTextField}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{ButtonStyles, Stylesheets, TextStyles}
import coinffeine.model.bitcoin.{Address, WalletProperties}
import coinffeine.model.currency._

class SendFundsForm(props: WalletProperties) {

  import SendFundsForm._

  require(props.balance.get.isDefined, "cannot run send funds form when balance is not defined")

  private val amount = new ObjectProperty[Bitcoin.Amount](this, "amount", 0.BTC)
  private val address = new ObjectProperty[Option[Address]](this, "address", None)
  private val submit = new BooleanProperty(this, "submit", false)

  private val formData = new VBox() {
    styleClass += "form-data"

    val currencyField = new CurrencyTextField(0.BTC) {
      amount <== currencyValue
    }

    content = Seq(
      selectLabel("amount to send", "send-amount"),
      new Button("Max") with ButtonStyles.Action {
        text <== props.balance.map { balance =>
          s"Max (${balance.get.available.format})"
        }
        onAction = () => {
          props.balance.get.foreach { balance =>
            currencyField.text.value = balance.available.value.toString()
          }
        }
      },
      currencyField,

      selectLabel("destination address", "send-address"),
      new TextField() {
        promptText = "Insert the destination address"
        address <== text.delegate.map { addr =>
          Try(new Address(null, addr)).toOption
        }
      }
    )
  }

  private val footer = new HBox {
    styleClass += "footer"
    content = Seq(
      new Button("Cancel") with ButtonStyles.Action {
        onAction = close _
      },
      new Button("Send") with ButtonStyles.Action {
        disable <== amount.delegate.mapToBool(a => !isValidAmount(a)) ||
          address.delegate.mapToBool(addr => addr.isEmpty)
        onAction = () => {
          submit.value = true
          close()
        }
      }
    )
  }

  private val content = new VBox() {
    styleClass += "wallet-send"
    content = Seq(formData, footer)
  }

  private def selectLabel(name: String, helpTopic: String) = new HBox() {
    styleClass += "line"
    content = Seq(
      new Label("Select the "),
      new Label(name) with TextStyles.Emphasis,
      new SupportWidget(helpTopic)
    )
  }

  private def isValidAmount(amount: Bitcoin.Amount): Boolean =
    amount.isPositive && amount <= props.balance.get.get.amount

  private val stage = new Stage(style = StageStyle.UTILITY) {
    title = "Send funds"
    initModality(Modality.APPLICATION_MODAL)
    scene = new CoinffeineScene(Stylesheets.Wallet) {
      root = SendFundsForm.this.content
    }
    centerOnScreen()
  }

  private def close(): Unit = {
    stage.close()
  }

  def show(): Result = {
    stage.showAndWait()
    if (submit.value) Send(amount.value, address.value.get) else CancelSend
  }
}

object SendFundsForm {
  sealed trait Result
  case object CancelSend extends Result
  case class Send(amount: Bitcoin.Amount, to: Address) extends Result
}
