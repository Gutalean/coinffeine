package coinffeine.gui.application.wallet

import scala.util.control.NonFatal
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout._
import scalafx.stage.{Modality, Stage, StageStyle}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.CurrencyTextField
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.model.bitcoin.{Address, WalletProperties}
import coinffeine.model.currency._

class WithdrawFundsForm(props: WalletProperties) {

  import WithdrawFundsForm._

  require(props.balance.get.isDefined, "cannot run withdraw funds form when balance is not defined")

  private val amount = new ObjectProperty[Bitcoin.Amount](this, "amount", 0.BTC)
  private val address = new ObjectProperty[Option[Address]](this, "address", None)
  private val submit = new BooleanProperty(this, "submit", false)

  private val content = new VBox() {
    id = "wallet-withdraw-root-pane"
    content = Seq(
      new VBox() {
        content = Seq(
          new Label("Select the amount to withdraw"),
          new HBox() {
            val currencyField = new CurrencyTextField(0.BTC) {
              amount <== currencyValue
            }
            content = Seq(
              currencyField,
              new Button("Max") {
                text <== props.balance.map {
                  case Some(balance) => s"Max (${balance.amount})"
                  case _ => "Max"
                }
                onAction = { action: Any =>
                  val balance = props.balance.get
                  if (balance.isDefined) {
                    currencyField.text.value = balance.get.amount.value.toString()
                  }
                }
              }
            )
          })
      },
      new VBox() {
        content = Seq(
          new Label("Select the destination address"),
          new TextField() {
            id = "wallet-withdraw-address-field"
            promptText = "Insert the destination Bitcoin address"
            address <== text.delegate.map { addr =>
              try { Some(new Address(null, addr)) }
              catch { case NonFatal(_) => None }
            }
          })
      },
      new TilePane() {
        content = Seq(
          new Button("Cancel") {
            maxWidth = Double.MaxValue
            onAction = { action: Any => close() }
          },
          new Button("Withdraw") {
            maxWidth = Double.MaxValue
            disable <== amount.delegate.mapToBool(a => !isValidAmount(a)) ||
              address.delegate.mapToBool(addr => addr.isEmpty)
            onAction = { action: Any =>
              submit.value = true
              close()
            }
          }
        )
      }
    )
  }

  private def isValidAmount(amount: Bitcoin.Amount): Boolean =
    amount.isPositive && amount <= props.balance.get.get.amount

  private val stage = new Stage(style = StageStyle.UTILITY) {
    title = "Withdraw funds"
    initModality(Modality.APPLICATION_MODAL)
    scene = new CoinffeineScene(Stylesheets.Wallet) {
      root = WithdrawFundsForm.this.content
    }
    centerOnScreen()
  }

  private def close(): Unit = {
    stage.close()
  }

  def show(): Result = {
    stage.showAndWait()
    if (submit.value) Withdraw(amount.value, address.value.get)
    else Cancel
  }
}

object WithdrawFundsForm {

  sealed trait Result

  case object Cancel extends Result

  case class Withdraw(amount: Bitcoin.Amount, to: Address) extends Result
}
