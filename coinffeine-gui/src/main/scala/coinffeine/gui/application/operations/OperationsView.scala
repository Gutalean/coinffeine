package coinffeine.gui.application.operations

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.scene.styles.{ButtonStyles, NodeStyles, OperationStyles, PaneStyles}
import coinffeine.model.market.Bid
import coinffeine.peer.api.CoinffeineApp

import scalafx.Includes._
import scalafx.event.Event
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

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
        new ProgressBar() { progress <== p.progressProperty },
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
    props.ordersProperty.bindToList(content) { p =>
      lineFor(p)
    }
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
    content = new Button("New order") with ButtonStyles.Action {
      onAction = { e: Event =>
        val form = new OrderSubmissionForm(app, orderValidation)
        form.show(delegate.getScene.getWindow)
      }
    }
  }
}
