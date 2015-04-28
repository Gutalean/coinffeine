package coinffeine.gui.application.operations

import javafx.beans.value.ObservableStringValue
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, Pane, VBox}
import scalafx.scene.{Node, Parent}
import scalafx.stage.{StageStyle, Modality, Stage, Window}

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{OrderStatusWidget, SupportWidget}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{OperationStyles, PaneStyles, Stylesheets}
import coinffeine.model.market.{AnyCurrencyOrder, Bid}

class OrderPropertiesDialog(props: OrderProperties) {

  private val dateTimePrinter = new DateTimePrinter

  private val action = if (props.typeProperty.value == Bid) "buying" else "selling"
  private val amount = props.amountProperty.value
  private val date = dateTimePrinter.printDate(props.createdOnProperty.value)

  private val icon = new Pane {
    props.orderProperty.delegate.bindToList(styleClass)(OperationStyles.stylesFor(_) :+ "icon")
  }

  private val summary = new Label {
    props.orderProperty.delegate.bindToList(styleClass)("summary" +: OperationStyles.stylesFor(_))
    text = s"You're $action $amount as of $date"
  }

  private val lines = new VBox {
    styleClass += "lines"
    content = Seq(
      makeStatusLine(
        props.statusProperty.delegate.mapToString(_.name.capitalize), props.orderProperty),
      makeLine("Amount", props.amountProperty.delegate.mapToString(_.toString)),
      makeLine("Type", props.typeProperty.delegate.mapToString(_.toString)),
      makeLine("Price", props.priceProperty.delegate.mapToString(_.toString)),
      makeLine("Order ID", props.idProperty.delegate.mapToString(_.value)),
      makeFooter()
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
    val stage = new Stage(StageStyle.UTILITY) {
      scene = formScene
      resizable = false
      initModality(Modality.WINDOW_MODAL)
      initOwner(parentWindow)
    }
    stage.setResizable(false)
    stage.show()
  }

  private def makeStatusLine(status: ObservableStringValue,
                             orderProperty: ReadOnlyObjectProperty[AnyCurrencyOrder]) =  new HBox {
    orderProperty.delegate.bindToList(styleClass)("line" +: OperationStyles.stylesFor(_))
    content = Seq(
      new VBox {
        content = Seq(
          new Label("Status") { styleClass += "prop-name" },
          new Label {
            styleClass += "prop-value"
            text <== status
          }
        )
      },
      new OrderStatusWidget {
        status <== orderProperty.delegate.map(OrderStatusWidget.Status.fromOrder)
      }
    )
  }

  private def makeLine(title: String,
                       value: ObservableStringValue): Node = new HBox {
    styleClass += "line"
    content = new VBox {
      content = Seq(
        new Label(title) { styleClass += "prop-name" },
        new Label {
          styleClass += "prop-value"
          text <== value
        }
      )
    }
  }

  private def makeFooter(): Node = new HBox {
    styleClass ++= Seq("line", "footer")
    content = new SupportWidget("order-props")
  }
}
