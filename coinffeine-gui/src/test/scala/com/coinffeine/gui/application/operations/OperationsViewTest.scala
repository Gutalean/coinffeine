package com.coinffeine.gui.application.operations

import javafx.collections.ObservableList
import javafx.scene.control.{Button, TableRow, TableView}
import javafx.scene.{Node, Parent}
import scalafx.scene.layout.Pane

import org.scalatest.concurrent.Eventually

import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Bid, OrderBookEntry}
import coinffeine.peer.api.CoinffeineApp
import coinffeine.peer.api.mock.MockCoinffeineApp
import com.coinffeine.gui.GuiTest
import com.coinffeine.gui.application.properties.OrderProperties

class OperationsViewTest extends GuiTest[Pane] with Eventually {

  val sampleOrder = OrderBookEntry(Bid, 1.BTC, 561.EUR)
  val app = new MockCoinffeineApp

  override def createRootNode(): Pane = {
    val view = new OperationsView(app)
    view.centerPane
  }

  "The operations view" must "show no orders when no orders was found" in new Fixture {
    ordersTable.itemsProperty().get() should be ('empty)
  }

  it must "show an order once submitted" in new Fixture {
    app.produceEvent(CoinffeineApp.OrderSubmittedEvent(sampleOrder))
    eventually {
      ordersTable.itemsProperty().get() should contain (OrderProperties(sampleOrder))
    }
  }

  it must "stop showing an order once cancelled" in new OrderIsPresentFixture {
    app.produceEvent(CoinffeineApp.OrderCancelledEvent(sampleOrder.id))
    eventually { ordersTable.itemsProperty().get() should be ('empty) }
  }

  it must "enable cancel button when an order is selected" in new OrderIsPresentFixture {
    cancelButton should be ('disabled)
    eventually { click(ordersTableRow(0)) }
    cancelButton should not be 'disabled
  }

  private def cancelButton = find[Button]("#cancelOrderBtn")
  private def ordersTable = find[TableView[OrderProperties]]("#ordersTable")

  private def ordersTableRow(rowIndex: Int): TableRow[OrderProperties] = {

    type RowType = TableRow[OrderProperties]

    def toRowTypeSeq(lst: ObservableList[Node]): Seq[RowType] = {
      var result: Seq[RowType] = Seq.empty
      val it = lst.iterator()
      while (it.hasNext) {
        result  = result :+ it.next().asInstanceOf[RowType]
      }
      result
    }

    def findTableRowChildren(node: Node): Seq[RowType] = {
      node match {
        case p: Parent =>
          val children = p.getChildrenUnmodifiable
          if (children.isEmpty) Seq.empty
          else if (children.get(0).isInstanceOf[RowType]) toRowTypeSeq(children)
          else children.toArray(new Array[Node](0)).toSeq.flatMap(findTableRowChildren)
        case _ => Seq.empty
      }
    }

    findTableRowChildren(ordersTable)(rowIndex)
  }

  trait OrderIsPresentFixture extends Fixture {
    app.produceEvent(CoinffeineApp.OrderSubmittedEvent(sampleOrder))
    eventually {
      ordersTable.itemsProperty().get() should contain (OrderProperties(sampleOrder))
    }
  }
}
