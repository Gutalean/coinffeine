package coinffeine.gui.application.operations

import java.net.URI
import javafx.beans.binding.{BooleanBinding, ObjectBinding}
import scala.concurrent.duration._
import scala.util.Try
import scalafx.Includes
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.stage.{Modality, Stage, Window}
import scalaz.NonEmptyList

import org.controlsfx.dialog.Dialog.Actions
import org.controlsfx.dialog.{DialogStyle, Dialogs}

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.{ObservableValueCombiner, PollingBean}
import coinffeine.gui.control.CurrencyTextField
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.Stylesheets
import coinffeine.gui.util.Browser
import coinffeine.model.bitcoin.TransactionSizeFeeCalculator
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.amounts
import coinffeine.peer.api.CoinffeineApp

class OrderSubmissionForm(app: CoinffeineApp, validation: OrderValidation) extends Includes {

  private val amountsCalculator = app.utils.exchangeAmountsCalculator
  private val maxFiatPerOrder = amountsCalculator.maxFiatPerExchange(Euro)
  private val aproxBitcoinFee =
    TransactionSizeFeeCalculator.defaultTransactionFee * amounts.HappyPathTransactions

  private val operationChoiceBox = new ChoiceBox[OrderType] {
    items = ObservableBuffer(OrderType.values)
    value = Bid
    prefWidth = 90
  }

  private val amountTextField = new CurrencyTextField(0.BTC) { id = "amount" }

  private val paymentProcessorChoiceBox = new ChoiceBox[String] {
    items = ObservableBuffer(Seq("OK Pay"))
    value = "OK Pay"
    disable = true
    prefWidth = 80
  }

  private val priceRadioButtonGroup = new ToggleGroup()

  private val marketPriceRadioButton = new RadioButton {
    text = "Market price order"
    selected = false
    toggleGroup = priceRadioButtonGroup
  }

  private val limitOrderRadioButton = new RadioButton {
    text = "Limit order"
    selected = true
    toggleGroup = priceRadioButtonGroup
  }

  private val limitTextField = new CurrencyTextField(0.EUR) { id = "limit" }

  private val limitOrderSelectedProperty = limitOrderRadioButton.selected

  private val bitcoinAmount: ObjectBinding[Option[Bitcoin.Amount]] =
    amountTextField.text.delegate.map(txt => Try(Bitcoin(BigDecimal(txt))).toOption)

  private val limitAmount: ObjectBinding[Option[Euro.Amount]] =
    limitTextField.text.delegate.map(txt => Try(Euro(BigDecimal(txt))).toOption)

  private val amountIsValid: BooleanBinding = bitcoinAmount.mapToBool(_.fold(false)(_.isPositive))

  private val limitIsValid: BooleanBinding =
    marketPriceRadioButton.selected or limitAmount.mapToBool(_.fold(false)(_.isPositive))

  private val currentQuote = PollingBean(OrderSubmissionForm.CurrentQuotePollingInterval) {
    app.marketStats.currentQuote(Market(Euro))
  }

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

  val root = new StackPane {
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
            new VBox {
              spacing = 20
              content = Seq(
                marketPriceRadioButton,
                new HBox() {
                  styleClass += "indented"
                  content = new Label(s"Bid market price starts at X EUR") {
                    text <== ObservableValueCombiner(operationChoiceBox.value, currentQuote) {
                      (op, quote) =>
                        quote match {
                          case Some(q) =>
                            val (target, price) = op match {
                              case Bid => Ask -> q.spread.lowestAsk
                              case Ask => Bid -> q.spread.highestBid
                            }
                            price match {
                              case Some(p) => s"$target market price starts at $p"
                              case None => s"No $target orders available in the market"
                            }
                          case None => "Retrieving current quotes..."
                        }
                    }
                  }
                }
              )
            },
            new VBox {
              id = "operations-submit-fee-summary"
              content = Seq(
                new HBox {
                  content = Seq(
                    new Label("You will pay aproximately the following fees"),
                    new Hyperlink("(know more):") {
                      styleClass += "smalltext"
                      onAction = { e: ActionEvent =>
                        Browser.default.browse(OrderSubmissionForm.OrderAmountsUrl)
                      }
                    }
                  )
                },
                new Label("\u27A4 0 BTC to Coinffeine") { styleClass += "indented" },
                new Label {
                  styleClass += "indented"
                  text <== operationChoiceBox.value.delegate.map {
                    case Bid => s"\u27A4 0 BTC to Bitcoin Network"
                    case Ask => s"\u27A4 ${aproxBitcoinFee.value} BTC to Bitcoin Network"
                  }
                },
                new Label {
                  styleClass += "indented"
                  text <== operationChoiceBox.value.delegate.map {
                    case Bid => s"\u27A4 0.5% EUR to OKPay"
                    case Ask => s"\u27A4 0 EUR to OKPay"
                  }
                }
              )
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
    currentQuote.close()
    stage.foreach(_.close())
  }

  private def submit(): Unit = {
    val price = priceRadioButtonGroup.selectedToggle.value match {
      case `limitOrderRadioButton` => LimitPrice(limitAmount.get.get)
      case `marketPriceRadioButton` => MarketPrice(Euro)
    }
    val order = Order.random(
      orderType = operationChoiceBox.value.value,
      amount = bitcoinAmount.get.get,
      price)
    if (shouldSubmit(order)) {
      app.network.submitOrder(order)
      closeForm()
    }
  }

  private def shouldSubmit(order: Order[Euro.type]): Boolean =
    validation.apply(order) match {
      case OrderValidation.OK => true

      case OrderValidation.Warning(unmetRequirements) =>
        askOrderSubmissionConfirmation(unmetRequirements)

      case OrderValidation.Error(unmetRequirements) =>
        informAboutRequirementsUnmet(unmetRequirements)
        false
    }

  private def informAboutRequirementsUnmet(
      violations: NonEmptyList[OrderValidation.Violation]): Unit = {
    val (title, message) =
      if (violations.size == 1)
        ("Cannot submit your order: " + violations.head.title, violations.head.description)
      else ("Cannot submit your order", violations.list.mkString("\n\n"))
    Dialogs.create()
      .style(DialogStyle.NATIVE)
      .title(title)
      .message(message.capitalize)
      .showInformation()
  }

  private def askOrderSubmissionConfirmation(
      violations: NonEmptyList[OrderValidation.Violation]): Boolean = {
    val title = violations.map(_.title).list.mkString(", ")
    val message = (violations.map(_.description).list :+
      "Do you want to proceed with the order submission?").mkString("\n\n")
    val result = Dialogs.create()
      .style(DialogStyle.NATIVE)
      .title(title)
      .message(message.capitalize)
      .actions(Actions.YES, Actions.NO)
      .showConfirm()
    result == Actions.YES
  }
}

object OrderSubmissionForm {
  val OrderAmountsUrl = new URI("https://github.com/coinffeine/coinffeine/wiki/order-amounts")
  val CurrentQuotePollingInterval = 10.seconds
}
