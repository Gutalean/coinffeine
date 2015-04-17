package coinffeine.gui.application.operations

import scala.concurrent.Future
import scala.concurrent.duration._
import scalafx.Includes._
import scalafx.event.Event
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

import org.joda.time.{DateTime, Period}

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.OrderStatusWidget
import coinffeine.gui.pane.PagePane
import coinffeine.gui.scene.styles.{ButtonStyles, OperationStyles, PaneStyles}
import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency._
import coinffeine.model.market.Market
import coinffeine.peer.api.CoinffeineApp

class OperationsView(app: CoinffeineApp,
                     props: ApplicationProperties,
                     orderValidation: OrderValidation) extends ApplicationView {

  private val now = PollingBean[DateTime](
    OperationsView.TimeComputingInterval)(Future.successful(DateTime.now()))

  private val dateTimePrinter = new DateTimePrinter

  private def lineFor(p: OrderProperties): Node = {
    val createdOn = p.createdOnProperty.value

    new StackPane {

      val lineWidth = width

      val progress = new HBox {
        content = new StackPane {
          styleClass += "progress"
          minWidth <== lineWidth * p.progressProperty
          visible <== p.statusProperty.delegate.mapToBool(_.isActive) and
            p.progressProperty.delegate.mapToBool(_.doubleValue() > 0.0d)
        }
      }

      val controls = new HBox {
        p.orderProperty.delegate.bindToList(styleClass)("line" +: OperationStyles.stylesFor(_))
        content = Seq(
          new StackPane {
            styleClass += "icon"
          },
          new OrderSummary(p.orderProperty),
          new Label {
            styleClass += "date"
            text <== now.mapToString { n =>
              val elapsed = new Period(createdOn, n.getOrElse(DateTime.now()))
              dateTimePrinter.printElapsed(createdOn, elapsed)
            }
          },
          new OrderStatusWidget {
            status <== p.orderProperty.delegate.map(OrderStatusWidget.Status.fromOrder)
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
              new Button with ButtonStyles.Close {
                visible <== p.statusProperty.delegate.mapToBool(_.isActive)
                onAction = { e: Event =>
                  app.network.cancelOrder(p.idProperty.value)
                }
              }
            )
          }
        )
      }
      content = Seq(progress, controls)
    }
  }

  private val operationsTable = new VBox {
    props.ordersProperty.bindToList(content)(lineFor)
  }

  override def name: String = "Operations"

  override def centerPane: Pane = new PagePane() {
    id = "operations-center-pane"
    headerText = "RECENT ACTIVITY"
    pageContent = operationsTable
  }

  override def controlPane: Pane = new VBox with PaneStyles.Centered {

    id = "operations-control-pane"

    val bitcoinPrice = new Label {
      styleClass += "btc-price"

      private val currentPrice = PollingBean(OperationsView.BitcoinPricePollingInterval) {
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

  private val BitcoinPricePollingInterval = 10.seconds
  private val TimeComputingInterval = 10.seconds
}
