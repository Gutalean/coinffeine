package coinffeine.gui.application.wallet

import scala.util.Try
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout._
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{CurrencyTextField, SupportWidget}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{ButtonStyles, Stylesheets, TextStyles}
import coinffeine.model.bitcoin.{BitcoinFeeCalculator, Address, WalletProperties}
import coinffeine.model.currency._

class SendFundsForm(props: WalletProperties, feeCalculator: BitcoinFeeCalculator) {

  import SendFundsForm._

  require(props.balance.get.isDefined, "cannot run send funds form when balance is not defined")

  private val txFee = s"${feeCalculator.defaultTransactionFee.value}BTC"

  private val amount = new ObjectProperty[Bitcoin.Amount](this, "amount", 0.BTC)
  private val address = new ObjectProperty[Option[Address]](this, "address", None)
  private val submit = new BooleanProperty(this, "submit", false)

  private val isValidAmount = amount.delegate.zip(props.balance) {
    case (a, Some(b)) => a.isPositive && a <= maxWithdraw(b)
    case _ => false
  }.mapToBool(identity)

  private val isValidAddress = address.delegate.mapToBool(_.isDefined)

  private val formData = new VBox() {
    styleClass += "form-data"

    val currencyField = new CurrencyTextField(0.BTC) {
      amount <== currencyValue
    }

    children = Seq(
      selectLabel("amount to send", "send-amount"),
      new Button("Max") with ButtonStyles.Action {
        text <== props.balance.map { balance =>
          s"Max (${maxWithdraw(balance.get)})"
        }
        onAction = () => {
          props.balance.get.foreach { balance =>
            currencyField.text.value = maxWithdraw(balance).value.toString()
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
      },
      new Label(s"A fee of $txFee will be paid to Bitcoin miners") {
        styleClass += "disclaimer"
      }
    )
  }

  private val footer = new HBox {
    styleClass += "footer"
    children = Seq(
      new Button("Cancel") with ButtonStyles.Action {
        onAction = close _
      },
      new Button("Send") with ButtonStyles.Action {
        disable <== isValidAmount.not() or isValidAddress.not()
        onAction = () => {
          submit.value = true
          close()
        }
      }
    )
  }

  private val dialogueContent = new VBox() {
    styleClass += "wallet-send"
    children = Seq(formData, footer)
    // Dirty hack: for some unknown reason (likely a JavaFX bug) this VBox is not resized
    // to its children dimensions. We must indicate by code (in CSS doesn't work) its min size.
    minWidth = 500
    minHeight = 500
  }

  private def selectLabel(name: String, helpTopic: String) = new HBox() {
    styleClass += "line"
    children = Seq(
      new Label("Select the "),
      new Label(name) with TextStyles.Emphasis,
      new SupportWidget(helpTopic)
    )
  }

  private def maxWithdraw(balance: BitcoinBalance) =
    (balance.available - feeCalculator.defaultTransactionFee).max(0.BTC)

  private val stage = new Stage(style = StageStyle.UTILITY) {
    title = "Send funds"
    resizable = false
    initModality(Modality.APPLICATION_MODAL)
    scene = new CoinffeineScene(Stylesheets.Wallet) {
      root = dialogueContent
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
