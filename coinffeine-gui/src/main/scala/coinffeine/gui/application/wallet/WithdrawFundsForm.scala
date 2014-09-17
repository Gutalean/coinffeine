package coinffeine.gui.application.wallet

import java.lang.Boolean
import scala.util.control.NonFatal
import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.geometry.{Insets, Orientation, Pos}
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout._
import scalafx.stage.{Modality, Stage, StageStyle}

import com.google.bitcoin.core.Address

import coinffeine.gui.control.CurrencyTextField
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.bitcoin.WalletProperties
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._

class WithdrawFundsForm(props: WalletProperties) {

  import WithdrawFundsForm._

  require(props.balance.get.isDefined, "cannot run withdraw funds form when balance is not defined")

  private val amount = new ObjectProperty[BitcoinAmount](this, "amount", 0.BTC)
  private val address = new ObjectProperty[Option[Address]](this, "address", None)
  private val submit = new BooleanProperty(this, "submit", false)

  private val content = new VBox(spacing = 25) {
    padding = Insets(20)
    content = Seq(
      new VBox(spacing = 5) {
        content = Seq(
          new Label("Select the amount to withdraw"),
          new HBox(spacing = 5) {
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
      new VBox(spacing = 5) {
        content = Seq(
          new Label("Select the destination address"),
          new TextField() {
            promptText = "Insert the destination Bitcoin address"
            minWidth = 300
            address <== text.delegate.map { addr =>
              try { Some(new Address(null, addr)) }
              catch { case NonFatal(_) => None }
            }
          })
      },
      new TilePane() {
        alignment = Pos.CENTER
        orientation = Orientation.HORIZONTAL
        hgap = 20
        content = Seq(
          new Button("Cancel") {
            maxWidth = Double.MaxValue
            onAction = { action: Any => close() }
          },
          new Button("Withdraw") {
            maxWidth = Double.MaxValue
            disable <== amount.delegate.mapToBool(a => !isValidAmount(a)) ||
              address.delegate.mapToBool(addr => !addr.isDefined)
            onAction = { action: Any =>
              submit.value = true
              close()
            }
          }
        )
      }
    )
  }

  private def isValidAddress(address: String): Boolean =
    address.length >= 26 && address.length <= 34

  private def isValidAmount(amount: BitcoinAmount): Boolean =
    amount.isPositive && amount <= props.balance.get.get.amount

  private val stage = new Stage(style = StageStyle.UTILITY) {
    title = "Withdraw funds"
    initModality(Modality.APPLICATION_MODAL)
    minWidth = 350
    maxWidth = 350
    minHeight = 225
    maxHeight = 225
    scene = new Scene(content) {
      stylesheets.add("/css/main.css")
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

  case class Withdraw(amount: BitcoinAmount, to: Address) extends Result
}
