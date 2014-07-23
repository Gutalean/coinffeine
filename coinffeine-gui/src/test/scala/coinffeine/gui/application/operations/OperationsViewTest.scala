package coinffeine.gui.application.operations

import javafx.collections.ObservableList
import javafx.scene.control.{Button, TableRow, TableView}
import javafx.scene.{Node, Parent}
import scala.collection.JavaConversions._
import scalafx.scene.layout.Pane

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.gui.application.ApplicationProperties
import coinffeine.gui.application.properties.OrderProperties
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Bid, CancelledOrder, Order, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.api.event.{OrderSubmittedEvent, OrderUpdatedEvent}
import coinffeine.peer.api.impl.MockCoinffeineApp

class OperationsViewTest extends GuiTest[Pane] with Eventually {

  val app = new MockCoinffeineApp

  override def createRootNode(): Pane = {
    val view = new OperationsView(app, new ApplicationProperties(app))
    view.centerPane
  }

  "The operations view" must "show no orders when no orders was found" in new Fixture {
    ordersTable.itemsProperty().get() should be ('empty)
  }

  it must "show an order once submitted" in new Fixture {
    val sampleOrder = Order(OrderId.random(), Bid, 1.BTC, 561.EUR)
    app.produceEvent(OrderSubmittedEvent(sampleOrder))
    eventually {
      find(sampleOrder.id) should be ('defined)
    }
  }

  it must "update the order status in the table" in new UpdatingOrderFixture {
    val newStatus = CancelledOrder("foobar")
    assertOnUpdate(_.withStatus(newStatus))(_.statusProperty.get should be (newStatus))
  }

  it must "enable cancel button when a cancellable order is selected" in new OrderIsPresentFixture {
    cancelButton should be ('disabled)
    eventually { click(ordersTableRow(0)) }
    cancelButton should not be 'disabled
  }

  it must "disable cancel button when a non-cancellable order is selected" in new OrderIsPresentFixture {
    cancelOrder()
    eventually { click(ordersTableRow(0)) }
    cancelButton should be ('disabled)
  }

  it must "disable cancel button when already selected order is cancelled" in new OrderIsPresentFixture {
    eventually { click(ordersTableRow(0)) }
    cancelOrder()
    eventually { cancelButton should be ('disabled) }
  }

  private def cancelButton = find[Button]("#cancelOrderBtn")
  private def ordersTable = find[TableView[OrderProperties]]("#ordersTable")
  private def shownOrders = ordersTable.itemsProperty().get()

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

  private def find(orderId: OrderId): Option[OrderProperties] =
    shownOrders.find(_.idProperty.value == orderId)

  trait OrderIsPresentFixture extends Fixture {
    val sampleOrder = Order(OrderId.random(), Bid, 1.BTC, 561.EUR)

    app.produceEvent(OrderSubmittedEvent(sampleOrder))
    eventually {
      find(sampleOrder.id) should be ('defined)
    }

    def cancelOrder(): Unit = {
      app.produceEvent(OrderUpdatedEvent(sampleOrder.withStatus(CancelledOrder("no reason"))))
    }
  }

  trait UpdatingOrderFixture extends OrderIsPresentFixture {
    def assertOnUpdate(alter: Order[FiatCurrency] => Order[FiatCurrency])
                      (assert: OrderProperties => Unit): Unit = {
      app.produceEvent(OrderUpdatedEvent(alter(sampleOrder)))
      eventually {
        assert(find(sampleOrder.id).get)
      }
    }
  }
}
