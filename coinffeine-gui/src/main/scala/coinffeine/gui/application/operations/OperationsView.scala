package coinffeine.gui.application.operations

import javafx.beans.binding.Bindings
import coinffeine.gui.scene.styles.PaneStyles

import scalafx.Includes._
import scalafx.event.{Event, ActionEvent}
import scalafx.scene.control._
import scalafx.scene.layout._

import org.controlsfx.dialog.Dialog.Actions
import org.controlsfx.dialog.{DialogStyle, Dialogs}

import coinffeine.gui.application.operations.validation.OrderValidation
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.beans.ObservableConstants
import coinffeine.peer.api.CoinffeineApp

class OperationsView(app: CoinffeineApp,
                     props: ApplicationProperties,
                     orderValidation: OrderValidation) extends ApplicationView {

  private val operationsTable = new OperationsTable(props.ordersProperty)

  private val cancellableOperationProperty = operationsTable.selected.delegate
    .flatMap {
      case Some(op) => op.isCancellable
      case None => ObservableConstants.False
    }
    .mapToBool(_.booleanValue())
    .toReadOnlyProperty

  private val cancelButton = new Button {
    id = "cancelOrderBtn"
    text = "Cancel"
    disable <== Bindings.not(cancellableOperationProperty)
    handleEvent(ActionEvent.Action) { () =>
      val confirm = Dialogs.create()
        .style(DialogStyle.NATIVE)
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
    content = Seq(cancelButton, toggleDetailsButton)
  }

  override def centerPane: Pane = new VBox {
    id = "operations-center-pane"
    vgrow = Priority.Always
    content = Seq(buttonsPane, jfxNode2sfx(operationsTable))
  }

  override def controlPane: Pane = new VBox with PaneStyles.Centered {
    content = new Button("New order") {
      onAction = { e: Event =>
        val form = new OrderSubmissionForm(app, orderValidation)
        form.show(delegate.getScene.getWindow)
      }
    }
  }
}
