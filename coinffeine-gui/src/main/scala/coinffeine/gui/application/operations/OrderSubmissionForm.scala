package coinffeine.gui.application.operations

import java.net.URI
import scala.util.Try
import scalafx.Includes
import scalafx.beans.property.BooleanProperty
import scalafx.collections.ObservableBuffer
import scalafx.event.{ActionEvent, Event}
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.stage.{Modality, Stage, Window}

import org.controlsfx.dialog.{Dialog, Dialogs}

import coinffeine.gui.control.CurrencyTextField
import coinffeine.gui.scene.{Stylesheets, CoinffeineScene}
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.CoinffeineApp

class OrderSubmissionForm(app: CoinffeineApp) extends Includes {

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

  private val limitLabel = new Label("Limit") {
    text <== when(operationChoiceBox.value.isEqualTo(Bid))
      .choose("By no more than")
      .otherwise("By no less than")
  }

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
                  content = Seq(limitLabel, limitTextField)
                }
              )
            },
            marketPriceRadioButton
          )
        },
        new HBox {
          id = "operations-submit-disclaimer-pane"
          content = Seq(
            new Label("These are gross amounts. Payment processor and Bitcoin fees are included.") {
              styleClass += "smalltext"
            },
            new Hyperlink("Know more.") {
              styleClass += "smalltext"
              onAction = { e: ActionEvent =>
                java.awt.Desktop.getDesktop.browse(OrderSubmissionForm.OrderAmountsUrl)
              }
            }
          )
        },
        new HBox {
          styleClass += "button-hpane"
          content = Seq(
            new Button {
              id = "cancel"
              text = "Cancel"
              handleEvent(ActionEvent.Action) { () =>
                closeForm()
              }
            },
            new Button {
              id = "submit"
              text = "Submit"
              disable <== amountIsValid.not() or limitIsValid.not()
              handleEvent(ActionEvent.Action) { () => submit() }
            })
        }
      )
    }
  }

  private var stage: Option[Stage] = None

  def show(parentWindow: Window): Unit = {
    val formScene = new CoinffeineScene(Stylesheets.Operations) {
      root = OrderSubmissionForm.this.root
    }
    stage = Some(new Stage {
      title = "Submit new order"
      scene = formScene
      resizable = false
      initModality(Modality.WINDOW_MODAL)
      initOwner(parentWindow)
    })
    operationChoiceBox.requestFocus()
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
      closeForm()
    }
  }

  private def checkPrerequisites(order: Order[Euro.type]): Boolean =
    checkFiatLimit(order) && checkNoSelfCross(order) &&
      checkEnoughFiatFunds(order) && checkEnoughBitcoinFunds(order)

  private def checkEnoughFiatFunds(order: Order[Euro.type]): Boolean = checkFunds(
    required = amountsCalculator.exchangeAmountsFor(order).fiatRequired(order.orderType),
    available = app.paymentProcessor.currentBalance().map(_.availableFunds)
  )

  private def checkEnoughBitcoinFunds(order: Order[Euro.type]): Boolean = checkFunds(
    required = amountsCalculator.exchangeAmountsFor(order).bitcoinRequired(order.orderType),
    available = app.wallet.balance.get.map(_.amount)
  )

  private def checkFiatLimit(order: Order[Euro.type]): Boolean = {
    val maxFiat = amountsCalculator.maxFiatPerExchange(Euro)
    if (order.price.value > maxFiat.value) {
      Dialogs.create()
        .title("Invalid fiat amount")
        .message(s"Cannot submit your order: maximum allowed fiat amount is $maxFiat")
        .showInformation()
      false
    } else true
  }

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

  private def checkNoSelfCross(order: Order[Euro.type]): Boolean = {
    order.orderType match {
      case Bid =>
        val askOrders = app.network.orders.values.filter(_.orderType == Ask)
        isSelfCrossed(order, askOrders)(_.value <= order.price.value)
      case Ask =>
        val bidOrders = app.network.orders.values.filter(_.orderType == Bid)
        isSelfCrossed(order, bidOrders)(_.value >= order.price.value)
    }
  }

  private def isSelfCrossed(order: Order[Euro.type], counterparts: Iterable[AnyCurrencyOrder])
                           (condition: Price[_ <: FiatCurrency] => Boolean): Boolean = {
    counterparts.find(c => condition(c.price)) match {
      case Some(selfCross) =>
        Dialogs.create()
          .title("Self cross detected")
          .message("This order would be self-crossing a previously submitted " +
            s"order of ${selfCross.price}")
          .showInformation()
        false
      case None => true
    }
  }
}

object OrderSubmissionForm {
  val OrderAmountsUrl = new URI("https://github.com/coinffeine/coinffeine/wiki/order-amounts")
}
