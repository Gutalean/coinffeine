package coinffeine.gui.application.operations.wizard

import scala.concurrent.duration._
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{Label, RadioButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}
import scalaz.Failure
import scalaz.syntax.std.option._

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.operations.wizard.OrderSubmissionWizard.CollectedData
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.{CurrencyTextField, GlyphIcon, SupportWidget}
import coinffeine.gui.wizard.{StepPane, StepPaneEvent}
import coinffeine.model.currency.{Bitcoin, Euro}
import coinffeine.model.market._
import coinffeine.model.order._
import coinffeine.peer.amounts.AmountsCalculator
import coinffeine.peer.api.MarketStats

class OrderAmountsStep(
    marketStats: MarketStats,
    amountsCalculator: AmountsCalculator,
    data: CollectedData,
    validator: OrderValidation)
    extends StepPane[OrderSubmissionWizard.CollectedData] {

  override val icon = GlyphIcon.MarketPrice

  private val order =
    new ObjectProperty[Option[OrderRequest]](this, "order", None)
  private val validation =
    new ObjectProperty[OrderValidation.Result](this, "validation", OrderValidation.Ok)

  private val currentQuote = PollingBean(OrderAmountsStep.CurrentQuotePollingInterval) {
    marketStats.currentQuote(Market(Euro))
  }

  private val action = new Label {
    styleClass += "action"
  }

  private val btcAmount = new CurrencyTextField(Bitcoin.zero) {
    styleClass += "btc-input"
  }

  private val fiatAmount = new CurrencyTextField(Euro.zero) {
    styleClass += "fiat-input"
  }

  private val marketPrice = new Label

  private object MarketSelection extends VBox {
    styleClass += "market-selection"

    val group = new ToggleGroup

    val limitButton = new RadioButton("Limit order") {
      toggleGroup = group
    }

    val limitDetails = new VBox {
      styleClass += "details"
      disable <== !limitButton.selected
      children = Seq(
        new HBox {
          styleClass += "price-line"
          children = Seq(
            new Label {
              text <== data.orderType.delegate.map {
                case Bid => "For no more than"
                case Ask => "For no less than"
                case _ => "For"
              }.toStr
            },
            fiatAmount,
            new Label("per BTC"))
        },
        new HBox {
          styleClass += "disclaimer"
          children = Seq(
            new Label {
              val maxFiat = amountsCalculator.maxFiatPerExchange(Euro)
              text = s"(Maximum allowed fiat per order is $maxFiat)"
            },
            new SupportWidget("limit-price")
          )
        }
      )
    }

    val marketPriceButton = new RadioButton("Market price order") {
      toggleGroup = group
    }

    val marketPriceDetails = new HBox {
      styleClass += "details"
      disable <== !marketPriceButton.selected
      children = Seq(marketPrice, new SupportWidget("market-price"))
    }

    val messages = new Label {
      styleClass += "messages"
      validation.delegate.bindToList(styleClass) { result =>
        Seq("label", "messages") ++ styleClassFor(result)
      }
      text <== validation.delegate.map {
        case Failure(OrderValidation.Warning(violations)) => violations.list.mkString("\n")
        case Failure(OrderValidation.Error(violations)) => violations.list.mkString("\n")
        case _ => ""
      }.toStr
    }

    children = Seq(limitButton, limitDetails, marketPriceButton, marketPriceDetails, messages)
  }

  private def styleClassFor(result: OrderValidation.Result): Option[String] = result match {
    case Failure(_: OrderValidation.Warning)=> "warning".some
    case Failure(_: OrderValidation.Error) => "error".some
    case _ => None
  }

  onActivation = { _: StepPaneEvent =>
    bindActionText()
    bindMarketPriceText()
    bindValidation()
    bindCanContinue()
    bindOutputData()
  }

  children = new VBox {
    styleClass += "order-amounts"
    children = Seq(action, btcAmount, MarketSelection)
  }

  private def bindActionText(): Unit = {
    action.text <== data.orderType.delegate.map(ot => s"I want to ${ot.toString.toLowerCase}").toStr
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
      order.map(o => validator(o, spread)).getOrElse(OrderValidation.Ok)
    }
  }

  private def bindCanContinue(): Unit = {
    val definedOrder = order.delegate.map(_.isDefined).toBool
    val validOrder = validation.delegate.map {
      case Failure(_: OrderValidation.Error) => false
      case _ => true
    }.toBool
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
