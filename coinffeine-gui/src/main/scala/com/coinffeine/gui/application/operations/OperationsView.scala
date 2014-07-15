package com.coinffeine.gui.application.operations

import coinffeine.model.market.OrderType
import org.controlsfx.dialog.Dialogs
import org.controlsfx.dialog.Dialog.Actions
import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.Insets
import scalafx.scene.control._
import scalafx.scene.control.TableColumn._
import scalafx.scene.layout._

import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import com.coinffeine.client.api.CoinffeineApp
import com.coinffeine.common._
import com.coinffeine.gui.application.{ApplicationProperties, ApplicationView}
import com.coinffeine.gui.application.properties.OrderProperties

class OperationsView(app: CoinffeineApp) extends ApplicationView {

  val props = new ApplicationProperties(app)

  private val orderTable = new TableView[OrderProperties](props.ordersProperty) {
    id = "ordersTable"
    placeholder = new Label("No orders found")
    columns ++= List(
      new TableColumn[OrderProperties, OrderType] {
        text = "Type"
        cellValueFactory = { _.value.orderTypeProperty }
        prefWidth = 100
      },
      new TableColumn[OrderProperties, BitcoinAmount] {
        text = "Amount"
        cellValueFactory = { _.value.amountProperty}
        prefWidth = 100
      },
      new TableColumn[OrderProperties, FiatAmount] {
        text = "Price"
        cellValueFactory = { _.value.priceProperty }
        prefWidth = 100
      }
    )
  }

  private val orderSelectionProperty = orderTable.selectionModel.value.selectedItemProperty()

  private val newOrderButton = new Button {
    id = "newOrderBtn"
    text = "New order"
    handleEvent(ActionEvent.ACTION) { () =>
      val form = new OrderSubmissionForm(app)
      form.show(delegate.getScene.getWindow)
    }
  }

  private val cancelOrderButton = new Button {
    id = "cancelOrderBtn"
    text = "Cancel order"
    disable <== orderSelectionProperty.isNull
    handleEvent(ActionEvent.ACTION) { () =>
      val confirm = Dialogs.create()
        .title("Order cancellation")
        .message("You are about to cancel the selected order. Are you sure?")
        .actions(Actions.YES, Actions.NO)
        .showConfirm()
      if (confirm == Actions.YES) {
        app.network.cancelOrder(orderSelectionProperty.getValue.order.id)
      }
    }
  }

  override def name: String = "Operations"

  override def centerPane: Pane = new GridPane {
    margin = Insets(20)
    hgap = 10
    vgap = 10
    add(newOrderButton, 0, 0)
    add(cancelOrderButton, 1, 0)
    add(orderTable, 0, 1, 3, 1)
  }
}
