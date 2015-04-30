package coinffeine.gui.application.operations

import javafx.collections.transformation.SortedList
import scala.concurrent.Future
import scala.concurrent.duration._
import scalafx.Includes._
import scalafx.beans.binding._
import scalafx.collections.ObservableBuffer
import scalafx.event.Event
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

import org.joda.time.{DateTime, Period}

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.PollingBean
import coinffeine.gui.control.{GlyphIcon, GlyphLabel, OrderStatusWidget}
import coinffeine.gui.pane.PagePane
import coinffeine.gui.scene.styles.{ButtonStyles, OperationStyles, PaneStyles}
import coinffeine.model.market.{Ask, Bid}
import coinffeine.peer.api.CoinffeineApp

class OperationsView(app: CoinffeineApp,
                     props: ApplicationProperties,
                     orderValidation: OrderValidation) extends ApplicationView {

  import OperationsView._

  private val now = PollingBean[DateTime](TimeComputingInterval)(Future.successful(DateTime.now()))

  private val dateTimePrinter = new DateTimePrinter

  private def lineFor(p: OrderProperties): Node = {
    val createdOn = p.createdOnProperty.value

    new StackPane {

      val lineWidth = width
      val barWidth = lineWidth * p.progressProperty
      val progressVisible = p.orderProperty.delegate.mapToBool { order =>
        order.status.isActive && order.progress > 0d
      }
      val textInside = p.progressProperty < ProgressThreshold

      val progress = new HBox {
        content = new StackPane {
          styleClass += "progress"
          minWidth <== barWidth
          visible <== progressVisible
        }
      }

      val progressText = new HBox {
        styleClass += "progress-text"
        visible <== progressVisible
        content = new Label {
          private val textInsideInsets = barWidth.delegate.map { size =>
            Insets(0, 0, 0, size.doubleValue() + ProgressTextPadding).delegate
          }
          text <== p.orderProperty.delegate.mapToString { order =>
            s"(${order.bitcoinsTransferred} transferred)"
          }
          minWidth <== Bindings.when(textInside).choose(0).otherwise(barWidth)
          alignment <== Bindings.when(textInside).choose(Pos.TopLeft).otherwise(Pos.TopRight)
          padding <== Bindings.when(textInside)
            .choose(textInsideInsets)
            .otherwise(Insets(0, ProgressTextPadding, 0, 0))
        }
      }

      val controls = new HBox {
        p.orderProperty.delegate.bindToList(styleClass)("line" +: OperationStyles.stylesFor(_))
        content = Seq(
          new GlyphLabel {
            styleClass += "icon"
            icon <== p.typeProperty.delegate.map {
              case Bid => GlyphIcon.Buy
              case Ask => GlyphIcon.Sell
            }
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
            online <== props.connectionStatusProperty.delegate.mapToBool(_.coinffeine.connected)
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
      content = Seq(progress, progressText, controls)
    }
  }

  private val operationsTable = new VBox {
    val sortedList = new ObservableBuffer(new SortedList[OrderProperties](
      props.ordersProperty.delegate, new CreationTimestampComparator))
    sortedList.bindToList(content)(lineFor)
  }

  override def name: String = "Operations"

  override def centerPane: Pane = new PagePane() {
    id = "operations-center-pane"
    headerText = "RECENT ACTIVITY"
    pageContent = operationsTable
  }

  override lazy val controlPane: Pane = new OperationsControlPane(app)
}

object OperationsView {

  private val TimeComputingInterval = 10.seconds

  /** Distance from the progress line to its legend text */
  private val ProgressTextPadding = 3d

  /** Minimum progress to move the legend inside the progress bar */
  private val ProgressThreshold = 0.2
}
