package coinffeine.gui.application.operations

import javafx.beans.binding.Bindings
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.scene.control._
import scalafx.scene.layout._

import org.controlsfx.dialog.Dialog.Actions
import org.controlsfx.dialog.Dialogs

import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.peer.api.CoinffeineApp

class OperationsView(app: CoinffeineApp, props: ApplicationProperties) extends ApplicationView {

  private val operationsTable = new OperationsTable(props.ordersProperty)

  private val cancellableOperationProperty =
    operationsTable.selected.delegate.mapToBool(_.exists(_.isCancellable.value)).toReadOnlyProperty

  private val newOrderButton = new Button {
    id = "newOrderBtn"
    text = "New order"
    handleEvent(ActionEvent.Action) { () =>
      val form = new OrderSubmissionForm(app)
      form.show(delegate.getScene.getWindow)
    }
  }

  private val cancelButton = new Button {
    id = "cancelOrderBtn"
    text = "Cancel"
    disable <== Bindings.not(cancellableOperationProperty)
    handleEvent(ActionEvent.Action) { () =>
      val confirm = Dialogs.create()
        .title("Order cancellation")
        .message("You are about to cancel the selected operation. Are you sure?")
        .actions(Actions.YES, Actions.NO)
        .showConfirm()
      if (confirm == Actions.YES) {
        operationsTable.selected.value.foreach {
          case order: OrderProperties => app.network.cancelOrder(order.orderIdProperty.value)
        }
      }
    }
  }

  private val toggleDetailsButton = new Button {
    id = "toggleDetailsButton"
    text <== operationsTable.showDetails.delegate.mapToString { shown =>
      if (shown) "Hide details" else "Show details"
    }
    disable <== operationsTable.selected.delegate.mapToBool(!_.isDefined)
    onAction = { action: Any =>
      operationsTable.showDetails.value = !operationsTable.showDetails.value
    }
  }

  override def name: String = "Operations"

  private val buttonsPane: Pane = new HBox {
    id = "operations-buttons-pane"
    content = Seq(newOrderButton, cancelButton, toggleDetailsButton)
  }

  override def centerPane: Pane = new VBox {
    id = "operations-center-pane"
    vgrow = Priority.Always
    content = Seq(buttonsPane, jfxNode2sfx(operationsTable))
  }
}
