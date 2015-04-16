package coinffeine.gui.application.operations

import scala.concurrent.duration._
import scalafx.Includes._
import scalafx.event.Event
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.OrderStatusWidget
import coinffeine.gui.scene.styles.{ButtonStyles, NodeStyles, OperationStyles, PaneStyles}
import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency._
import coinffeine.model.market.{AnyCurrencyOrder, Bid, Market}
import coinffeine.peer.api.CoinffeineApp

class OperationsView(app: CoinffeineApp,
                     props: ApplicationProperties,
                     orderValidation: OrderValidation) extends ApplicationView {

  private def lineFor(p: OrderProperties): Node = {
    val action = if (p.orderTypeProperty.value == Bid) "buying" else "selling"
    val amount = p.amountProperty.value
    new HBox {
      styleClass ++= Seq("line", OperationStyles.styleClassFor(p))
      content = Seq(
        new StackPane { styleClass += "icon" },
        new Label(s"You are $action $amount") { styleClass += "summary" },
        new Label("3d 20h ago") { styleClass += "date" },
        new OrderStatusWidget {
          status <== p.sourceProperty.delegate.map {
            case order: AnyCurrencyOrder => OrderStatusWidget.Status.fromOrder(order)
          }
        },
        new HBox with PaneStyles.ButtonRow {
          styleClass += "buttons"
          content = Seq(
            new Button with ButtonStyles.Details {
              onAction = { e: Event =>
                val dialog = new OrderPropertiesDialog(p)
                dialog.show(delegate.getScene.getWindow)
              }
            },
            new Button with ButtonStyles.Close)
        }
      )
    }
  }

  private val operationsTable = new VBox {
    props.ordersProperty.bindToList(content)(lineFor)
  }

  override def name: String = "Operations"

  override def centerPane: Pane = new VBox {
    id = "operations-center-pane"
    vgrow = Priority.Always
    content = Seq(
      new HBox with PaneStyles.Centered {
        styleClass += "header"
        content = new Label("RECENT ACTIVITY")
      },
      new ScrollPane() with NodeStyles.VExpand { content = operationsTable }
    )
  }

  override def controlPane: Pane = new VBox with PaneStyles.Centered {

    id = "operations-control-pane"

    val bitcoinPrice = new Label {
      styleClass += "btc-price"

      private val currentPrice = PollingBean(OrderSubmissionForm.CurrentQuotePollingInterval) {
        implicit val executor = FxExecutor.asContext
        app.marketStats.currentQuote(Market(Euro)).map(_.lastPrice)
      }

      text <== currentPrice.mapToString {
        case Some(Some(p)) => s"1 BTC = ${p.of(1.BTC)}"
        case _ => s"1 BTC = ${CurrencyAmount.formatMissing(Euro)}"
      }
    }

    val newOrderButton = new Button("New order") with ButtonStyles.Action {
      onAction = { e: Event =>
        val form = new OrderSubmissionForm(app, orderValidation)
        form.show(delegate.getScene.getWindow)
      }
    }
    content = Seq(bitcoinPrice, newOrderButton)
  }
}

object OperationsView {

  val BitcoinPricePollingInterval = 10.seconds
}
