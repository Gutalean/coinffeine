package coinffeine.gui.application.operations.wizard

import scala.concurrent.duration._
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{Label, RadioButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.{CurrencyTextField, GlyphIcon, SupportWidget}
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.model.currency.{FiatCurrency, Bitcoin, Euro}
import coinffeine.model.market._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.api.MarketStats

class OrderAmountsStep(marketStats: MarketStats,
                       amountsCalculator: AmountsCalculator,
                       data: CollectedData,
                       validator: OrderValidation)
  extends StepPane[OrderSubmissionWizard.CollectedData] {

  override val icon = GlyphIcon.MarketPrice

  private val order =
    new ObjectProperty[Option[OrderRequest[Euro.type]]](this, "order", None)
  private val validation =
    new ObjectProperty[OrderValidation.Result](this, "validation", OrderValidation.OK)

  private val currentQuote = PollingBean(OrderAmountsStep.CurrentQuotePollingInterval) {
    marketStats.currentQuote(Market(Euro))
  }

  private val action = new Label { styleClass += "action" }

  private val btcAmount = new CurrencyTextField(Bitcoin(0)) { styleClass += "btc-input" }

  private val fiatAmount = new CurrencyTextField(Euro(0)) { styleClass += "fiat-input" }

  private val marketPrice = new Label

  private object MarketSelection extends VBox {
    styleClass += "market-selection"

    val group = new ToggleGroup

    val limitButton = new RadioButton("Limit order") { toggleGroup = group }

    val limitDetails = new VBox {
      styleClass += "details"
      disable <== !limitButton.selected
      content = Seq(
        new HBox {
          styleClass += "price-line"
          content = Seq(
            new Label {
              text <== data.orderType.delegate.mapToString {
                case Bid => "For no more than"
                case Ask => "For no less than"
                case _ => "For"
              }
            },
            fiatAmount,
            new Label("per BTC"))
        },
        new HBox {
          styleClass += "disclaimer"
          content = Seq(
            new Label {
              val maxFiat = amountsCalculator.maxFiatPerExchange(Euro)
              text = s"(Maximum allowed fiat per order is $maxFiat)"
            },
            new SupportWidget("limit-price")
          )
        }
      )
    }

    val marketPriceButton = new RadioButton("Market price order") { toggleGroup = group }

    val marketPriceDetails = new HBox {
      styleClass += "details"
      disable <== !marketPriceButton.selected
      content = Seq(marketPrice, new SupportWidget("market-price"))
    }

    val messages = new Label {
      styleClass += "messages"
      text <== validation.delegate.mapToString {
        case OrderValidation.OK => ""
        case OrderValidation.Warning(violations) => violations.list.mkString("\n")
        case OrderValidation.Error(violations) => violations.list.mkString("\n")
      }
    }

    content = Seq(limitButton, limitDetails, marketPriceButton, marketPriceDetails, messages)
  }

  onActivation = { _: StepPaneEvent =>
    bindActionText()
    bindMarketPriceText()
    bindValidation()
    bindCanContinue()
    bindOutputData()
  }

  content = new VBox {
    styleClass += "order-amounts"
    content = Seq(action, btcAmount, MarketSelection)
  }

  private def bindActionText(): Unit = {
    action.text <== data.orderType.delegate.mapToString(
      ot => s"I want to ${ot.toString.toLowerCase}")
  }

  private def bindMarketPriceText(): Unit = {
    marketPrice.text <== data.orderType.delegate.zip(currentQuote) {
      case (op, Some(quote)) =>
        val price = op match {
          case Bid => quote.spread.lowestAsk
          case Ask => quote.spread.highestBid
        }
        price.fold(s"No ${op.oppositeType} orders available in the market") { p =>
          s"${op.oppositeType} market price starts at $p"
        }
      case _ => "Retrieving current quotes..."
    }
  }

  private def bindValidation(): Unit = {
    order <== data.orderType.delegate.zip(data.bitcoinAmount, data.price) {
      (orderType, nullableAmount, nullablePrice) =>
        for {
          amount <- Option(nullableAmount) if amount.isPositive
          price <- Option(nullablePrice)
        } yield OrderRequest(orderType, nullableAmount, price)
    }

    validation <== order.delegate.zip(currentQuote) { (order, quote) =>
      val spread = quote.map(_.spread).getOrElse(Spread.empty)
      order.map(o => validator(o, spread)).getOrElse(OrderValidation.OK)
    }
  }

  private def bindCanContinue(): Unit = {
    val definedOrder = order.delegate.mapToBool(_.isDefined)
    val validOrder = validation.delegate.mapToBool {
      case _: OrderValidation.Error => false
      case _ => true
    }
    canContinue <== definedOrder and validOrder
  }

  private def bindOutputData(): Unit = {
    data.bitcoinAmount <== btcAmount.currencyValue

    data.price <==
      MarketSelection.group.selectedToggle.delegate.zip(fiatAmount.currencyValue) {
        (sel, price) => Option(sel) match {
          case Some(MarketSelection.limitButton) if price.isPositive => LimitPrice(price)
          case Some(MarketSelection.marketPriceButton) => MarketPrice(Euro)
          case _ => null
        }
      }
  }
}

object OrderAmountsStep {
  val CurrentQuotePollingInterval = 20.seconds
}
