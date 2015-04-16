package coinffeine.gui.application.operations

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.{Label, ProgressBar}
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.scene.{Node, Parent}
import scalafx.stage.{Modality, Stage, Window}
import javafx.beans.value.{ObservableDoubleValue, ObservableStringValue}

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.OrderStatusWidget
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{OperationStyles, PaneStyles, Stylesheets}
import coinffeine.model.market.{AnyCurrencyOrder, Bid}

class OrderPropertiesDialog(props: OrderProperties) {

  private val action = if (props.typeProperty.value == Bid) "buying" else "selling"
  private val amount = props.amountProperty.value

  private val icon = new StackPane {
    styleClass += "icon"
    styleClass += (if (props.typeProperty.value == Bid) "buy-icon" else "sell-icon")
  }

  private val summary = new Label {
    styleClass ++= Seq("summary", OperationStyles.styleClassFor(props))
    text = s"You're $action $amount as of April 4, 2015"
  }

  private val lines = new VBox {
    styleClass += "lines"
    content = Seq(
      makeStatusLine(
        props.statusProperty.delegate.mapToString(_.name.capitalize), props.orderProperty),
      makeLine("Amount", props.amountProperty.delegate.mapToString(_.toString)),
      makeLine("Type", props.typeProperty.delegate.mapToString(_.toString)),
      makeLine("Price", props.priceProperty.delegate.mapToString(_.toString)),
      makeLine("Order ID", props.idProperty.delegate.mapToString(_.value))
    )
  }

  private val root: Parent = new VBox with PaneStyles.Centered {
    styleClass += "order-props"
    content = Seq(icon, summary, lines)
  }

  def show(parentWindow: Window): Unit = {
    val formScene = new CoinffeineScene(Stylesheets.Operations) {
      root = OrderPropertiesDialog.this.root
    }
    val stage = new Stage {
      scene = formScene
      resizable = false
      initModality(Modality.WINDOW_MODAL)
      initOwner(parentWindow)
    }
    stage.show()
  }

  private def makeStatusLine(status: ObservableStringValue,
                             orderProperty: ReadOnlyObjectProperty[AnyCurrencyOrder]) = makeLine(
    title = "Status",
    value = status,
    valueClass = Some(OperationStyles.styleClassFor(props)),
    companionNode = Some(new OrderStatusWidget {
      status <== orderProperty.delegate.map(OrderStatusWidget.Status.fromOrder)
    })
  )

  private def makeLine(title: String,
                       value: ObservableStringValue,
                       valueClass: Option[String] = None,
                       companionNode: Option[Node] = None): Node = new HBox {
    styleClass ++= Seq("line") ++ valueClass
    content = Seq(
      new VBox {
        content = Seq(
          new Label(title) { styleClass += "prop-name" },
          new Label {
            styleClass += "prop-value"
            text <== value
          }
        )
      }
    )
    companionNode.foreach(content.add(_))
  }
}
