package coinffeine.gui.application.operations

import javafx.beans.value.{ObservableDoubleValue, ObservableStringValue}

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{OperationStyles, PaneStyles, Stylesheets}
import coinffeine.model.market.Bid

import scalafx.scene.control.{Label, ProgressBar}
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.scene.{Node, Parent}
import scalafx.stage.{Modality, Stage, Window}

class OrderPropertiesDialog(props: OrderProperties) {

  private val action = if (props.orderTypeProperty.value == Bid) "buying" else "selling"
  private val amount = props.amountProperty.value

  private val icon = new StackPane {
    styleClass += "icon"
    styleClass += (if (props.orderTypeProperty.value == Bid) "buy-icon" else "sell-icon")
  }

  private val summary = new Label {
    styleClass ++= Seq("summary", OperationStyles.styleClassFor(props))
    text = s"You're $action $amount as of April 4, 2015"
  }

  private val lines = new VBox {
    styleClass += "lines"
    content = Seq(
      makeStatusLine(props.statusProperty, props.progressProperty),
      makeLine("Amount", props.amountProperty.delegate.mapToString(_.toString)),
      makeLine("Type", props.orderTypeProperty.delegate.mapToString(_.toString)),
      makeLine("Price", props.priceProperty.delegate.mapToString(_.toString)),
      makeLine("Order ID", props.orderIdProperty.delegate.mapToString(_.value))
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
                             orderProgress: ObservableDoubleValue) = makeLine(
    title = "Status",
    value = status,
    valueClass = Some(OperationStyles.styleClassFor(props)),
    companionNode = Some(new ProgressBar { progress <== orderProgress })
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
