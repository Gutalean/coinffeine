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

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.CurrencyTextField
import coinffeine.gui.scene.{CoinffeineScene, Stylesheets}
import coinffeine.gui.util.Browser
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.CoinffeineApp

class OrderSubmissionForm(app: CoinffeineApp) extends Includes {

  private val amountsCalculator = app.utils.exchangeAmountsCalculator
  private val maxFiatPerOrder = amountsCalculator.maxFiatPerExchange(Euro)

  private val operationChoiceBox = new ChoiceBox[OrderType] {
    items = ObservableBuffer(OrderType.values)
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
      .choose("For no more than")
      .otherwise("For no less than")
  }

  private val totalFiat =
    createObjectBinding(amountTextField.currencyValue, limitTextField.currencyValue) { (btcs, fiat) =>
      try { Price(fiat).of(btcs) }
      catch {
        case e: IllegalArgumentException => 0.EUR
      }
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
                  content = Seq(
                    limitLabel,
                    limitTextField,
                    new Label("per BTC (0.00 EUR total)") {
                      text <== totalFiat.mapToString { f => s"per BTC ($f total)" }
                    })
                },
                new HBox() {
                  styleClass += ("indented", "smalltext")
                  content = new Label(s"Maximum allowed fiat per order is $maxFiatPerOrder")
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
                Browser.default.browse(OrderSubmissionForm.OrderAmountsUrl)
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
    val order = Order.random(
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
    val requestedFiat = order.price.of(order.amount)
    if (requestedFiat > maxFiatPerOrder) {
      Dialogs.create()
        .title("Invalid fiat amount")
        .message("Cannot submit your order: " +
          s"maximum allowed fiat amount is $maxFiatPerOrder, but you requested $requestedFiat")
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
        val askOrders = app.network.orders.values.filter(o => suitableForSelfCross(o, Ask))
        isSelfCrossed(order, askOrders)(_.value <= order.price.value)
      case Ask =>
        val bidOrders = app.network.orders.values.filter(o => suitableForSelfCross(o, Bid))
        isSelfCrossed(order, bidOrders)(_.value >= order.price.value)
    }
  }

  private def suitableForSelfCross(order: AnyCurrencyOrder, orderType: OrderType) =
    order.status.isActive && order.orderType == orderType

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
