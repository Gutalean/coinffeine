package coinffeine.gui.application.operations

import javafx.beans.value.ObservableStringValue
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, Pane, VBox}
import scalafx.scene.{Node, Parent}
import scalafx.stage._

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{OrderStatusWidget, SupportWidget}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{OperationStyles, PaneStyles, Stylesheets}
import coinffeine.gui.util.ElapsedTimePrinter
import coinffeine.model.exchange.ExchangeStatus.Handshaking
import coinffeine.model.order._

class OrderPropertiesDialog(props: OrderProperties) {

  private val elapsedTimePrinter = new ElapsedTimePrinter

  private val action = if (props.typeProperty.value == Bid) "buying" else "selling"
  private val amount = props.amountProperty.value
  private val date = elapsedTimePrinter.printDate(props.createdOnProperty.value)

  private val icon = new Pane {
    props.orderProperty.delegate.bindToList(styleClass)(OperationStyles.stylesFor(_) :+ "icon")
  }

  private val summary = new Label {
    props.orderProperty.delegate.bindToList(styleClass)("summary" +: OperationStyles.stylesFor(_))
    text = s"You're $action $amount as of $date"
  }

  private val lines = new VBox {
    styleClass += "lines"
    children = Seq(
      statusLine(),
      amountLine(),
      orderTypeLine(),
      priceLine(),
      orderIdLine(),
      footer()
    )
  }

  private val root: Parent = new VBox with PaneStyles.Centered {
    styleClass += "order-props"
    children = Seq(icon, summary, lines)
  }

  def show(parentWindow: Window): Unit = {
    val formScene = new CoinffeineScene(Stylesheets.Operations) {
      root = OrderPropertiesDialog.this.root
    }
    val stage = new Stage(StageStyle.UTILITY) {
      scene = formScene
      resizable = false
      initModality(Modality.WINDOW_MODAL)
      initOwner(parentWindow)
    }
    stage.setResizable(false)
    stage.show()
  }

  private def statusLine() = new HBox {
    props.orderProperty.delegate.bindToList(styleClass)("line" +: OperationStyles.stylesFor(_))
    children = Seq(
      new VBox {
        children = Seq(
          propName("Status"),
          propValue(props.statusProperty.delegate.mapToString(_.name.capitalize))
        )
      },
      new OrderStatusWidget {
        status <== props.orderProperty.delegate.map(OrderStatusWidget.Status.fromOrder)
      }
    )
  }

  private def amountLine() = {
    simpleLine("Amount", props.amountProperty.delegate.mapToString(_.toString))
  }

  private def orderTypeLine() = {
    simpleLine("Type", props.typeProperty.delegate.mapToString(_.toString))
  }

  private def priceLine() = {
    val requestedPrice = props.priceProperty.delegate.mapToString(formatRequestedPrice)
    val actualPrice = props.orderProperty.delegate.mapToString(formatAveragePrice)
    simpleLine("Price", requestedPrice, actualPrice)
  }

  private def formatRequestedPrice(price: OrderPrice): String = price match {
    case LimitPrice(limit) => "Limit price at " + limit
    case MarketPrice(currency) => s"Taking $currency market price"
  }

  def formatAveragePrice(order: Order): String = {
    val exchanges = order.exchanges.values.filter(_.status != Handshaking).toSeq
    WeightedAveragePrice.average(exchanges).fold("") { price =>
      "Average of %s from %s".format(price, formatCount(exchanges.size, "exchange"))
    }
  }

  private def formatCount(number: Int, unit: String): String = number match {
    case 0 => s"no ${unit}s"
    case 1 => s"one $unit"
    case _ => s"$number ${unit}s"
  }

  private def orderIdLine() = {
    simpleLine("Order ID", props.idProperty.delegate.mapToString(_.value))
  }

  private def simpleLine(
      title: String, values: ObservableStringValue*): Node = new HBox {
    styleClass += "line"
    children = new VBox {
      children = propName(title) +: values.map(propValue)
    }
  }

  private def propName(text: String) = new Label(text) { styleClass += "prop-name" }

  private def propValue(value: ObservableStringValue) = new Label {
    styleClass += "prop-value"
    text <== value
  }

  private def footer(): Node = new HBox {
    styleClass ++= Seq("line", "footer")
    children = new SupportWidget("order-props")
  }
}
