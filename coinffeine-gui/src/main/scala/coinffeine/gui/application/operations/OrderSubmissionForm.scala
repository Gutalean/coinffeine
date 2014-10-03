package coinffeine.gui.application.operations

import scala.util.Try
import scalafx.beans.property.BooleanProperty
import scalafx.collections.ObservableBuffer
import scalafx.event.{ActionEvent, Event}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.stage.{Modality, Stage, Window}

import org.controlsfx.dialog.{Dialog, Dialogs}

import coinffeine.gui.control.CurrencyTextField
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.CoinffeineApp

class OrderSubmissionForm(app: CoinffeineApp) {

  private val amountsCalculator = app.utils.exchangeAmountsCalculator

  private val operationChoiceBox = new ChoiceBox[OrderType] {
    items = ObservableBuffer(Seq(Bid, Ask))
    value = Bid
    prefWidth = 90
  }

  private val amountTextField = new CurrencyTextField(0.BTC) { id = "amount" }

  private def bitcoinAmount: Try[Bitcoin.Amount] = Try {
    Bitcoin(BigDecimal(amountTextField.text.getValueSafe))
  }

  private def limitAmount: Try[Euro.Amount] = Try {
    Euro(BigDecimal(limitTextField.text.getValueSafe))
  }

  private val amountIsValid = new BooleanProperty(this, "AmountIsValid", false)
  private val limitIsValid = new BooleanProperty(this, "LimitIsValid", false)

  private val paymentProcessorChoiceBox = new ChoiceBox[String] {
    items = ObservableBuffer(Seq("OK Pay"))
    value = "OK Pay"
    disable = true
    prefWidth = 80
  }

  private val priceRadioButtonGroup = new ToggleGroup()

  private val marketPriceRadioButton = new RadioButton {
    text = "Market price order (not supported yet)"
    selected = false
    disable = true // disabled until market price is supported
    toggleGroup = priceRadioButtonGroup
  }

  private val limitOrderRadioButton = new RadioButton {
    text = "Limit order"
    selected = true
    toggleGroup = priceRadioButtonGroup
  }

  private val limitTextField = new CurrencyTextField(0.EUR) { id = "limit" }

  private val limitOrderSelectedProperty = limitOrderRadioButton.selected

  amountTextField.handleEvent(Event.ANY) { () => handleSubmitButtonEnabled() }
  limitTextField.handleEvent(Event.ANY) { () => handleSubmitButtonEnabled() }

  private def handleSubmitButtonEnabled(): Unit = {
    amountIsValid.value = bitcoinAmount.map(_.isPositive).getOrElse(false)
    limitIsValid.value = limitAmount.map(_.isPositive).getOrElse(false)
  }

  val root = new StackPane {
    id = "operations-submit-root-pane"
    content = new VBox {
      id = "operations-submit-content-pane"
      content = Seq(
        new HBox {
          styleClass += "operations-submit-declaration-pane"
          content = Seq(
            new Label("I want to"),
            operationChoiceBox,
            amountTextField,
            new Label(" using "),
            paymentProcessorChoiceBox
          )
        },
        new VBox {
          spacing = 20
          content = Seq(
            new VBox {
              spacing = 20
              content = Seq(
                limitOrderRadioButton,
                new HBox {
                  styleClass += ("operations-submit-declaration-pane", "indented")
                  disable <== limitOrderSelectedProperty.not()
                  content = Seq(new Label("Limit"), limitTextField)
                }
              )
            },
            marketPriceRadioButton
          )
        },
        new HBox {
          styleClass += "button-hpane"
          content = Seq(
            new Button {
              id = "cancel"
              text = "Cancel"
              handleEvent(ActionEvent.ACTION) { () =>
                closeForm()
              }
            },
            new Button {
              id = "submit"
              text = "Submit"
              disable <== amountIsValid.not() or limitIsValid.not()
              handleEvent(ActionEvent.ACTION) { () => submit() }
            })
        }
      )
    }
  }

  private var stage: Option[Stage] = None

  def show(parentWindow: Window): Unit = {
    val formScene = new Scene() {
      root = OrderSubmissionForm.this.root
      stylesheets.add("/css/controls.css")
      stylesheets.add("/css/main.css")
      stylesheets.add("/css/operations.css")
    }
    stage = Some(new Stage {
      title = "Submit new order"
      scene = formScene
      resizable = false
      initModality(Modality.WINDOW_MODAL)
      initOwner(parentWindow)
    })
    stage.get.show()
  }

  private def closeForm(): Unit = {
    stage.foreach(_.close())
  }

  private def submit(): Unit = {
    val order = Order(
      orderType = operationChoiceBox.value.value,
      amount = bitcoinAmount.get,
      price = Price(limitAmount.get))
    if (checkPrerequisites(order)) {
      app.network.submitOrder(order)
    }
    closeForm()
  }

  private def checkPrerequisites(order: Order[Euro.type]): Boolean =
    checkEnoughFiatFunds(order) && checkEnoughBitcoinFunds(order)

  private def checkEnoughFiatFunds(order: Order[Euro.type]): Boolean = checkFunds(
    required = amountsCalculator.exchangeAmountsFor(order).fiatRequired(order.orderType),
    available = app.paymentProcessor.currentBalance().map(_.availableFunds)
  )

  private def checkEnoughBitcoinFunds(order: Order[Euro.type]): Boolean = checkFunds(
    required = amountsCalculator.exchangeAmountsFor(order).bitcoinRequired(order.orderType),
    available = app.wallet.balance.get.map(_.amount)
  )

  private def checkFunds[Amount <: CurrencyAmount[_]](
      required: Amount, available: Option[Amount]): Boolean = {
    val currency = required.currency
    available match {
      case Some(balance) if required <= balance => true
      case Some(balance) =>
        val response = Dialogs.create()
          .title(s"Insufficient $currency funds")
          .message(
            s"""Your $balance balance is insufficient to submit this order (at least $required required).
               |
               |You may proceed, but your order will be stalled until enough funds are available.
               |
               |Do you want to proceed with the order submission?""".stripMargin)
          .showConfirm()
        response == Dialog.Actions.YES
      case None =>
        val response = Dialogs.create()
          .title(s"Unavailable $currency funds")
          .message(
            s"""It's not possible to check your $currency balance. Therefore it cannot be checked to verify the correctness of this order.
              |
              |It can be submitted anyway, but it might be stalled until your balance is available again and it has enough funds to satisfy the order.
              |
              |Do you want to proceed with the order submission?""".stripMargin)
          .showConfirm()
        response == Dialog.Actions.YES
    }
  }
}
