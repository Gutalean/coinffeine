package coinffeine.gui.application.operations

import javafx.collections.ObservableList
import javafx.scene.control._
import javafx.scene.{Node, Parent}
import scala.collection.JavaConversions._
import scalafx.scene.layout.Pane

import org.scalatest.concurrent.Eventually

import coinffeine.gui.GuiTest
import coinffeine.gui.application.ApplicationProperties
import coinffeine.gui.application.properties.OperationProperties
import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.impl.MockCoinffeineApp

class OperationsViewTest extends GuiTest[Pane] with Eventually {

  val app = new MockCoinffeineApp

  override def createRootNode(): Pane = {
    val properties = new ApplicationProperties(app, FxExecutor.asContext)
    val view = new OperationsView(app, properties, DummyOrderValidation)
    view.centerPane
  }

  "The operations view" must "show no orders when no orders was found" in new Fixture {
    ordersTable.getRoot.getChildren shouldBe 'empty
  }

  it must "show an order once submitted" in new Fixture {
    val sampleOrder = Order(OrderId.random(), Bid, 1.BTC, Price(561.EUR))
    app.network.orders.set(sampleOrder.id, sampleOrder)
    eventually {
      find(sampleOrder.id) shouldBe 'defined
    }
  }

  it must "update the order status in the table" in new OrderIsPresentFixture {
    app.network.orders.set(sampleOrder.id, sampleOrder.cancel)
    eventually {
      find(sampleOrder.id).get.statusProperty.get shouldBe CancelledOrder.name.capitalize
    }
  }

  it must "enable cancel button when a cancellable order is selected" in new OrderIsPresentFixture {
    cancelButton shouldBe 'disabled
    eventually { click(ordersTableRow(0)) }
    cancelButton should not be 'disabled
  }

  it must "disable cancel button when a non-cancellable order is selected" in new OrderIsPresentFixture {
    cancelOrder()
    eventually { click(ordersTableRow(3)) }
    cancelButton shouldBe 'disabled
  }

  it must "disable cancel button when already selected order is cancelled" in new OrderIsPresentFixture {
    eventually { click(ordersTableRow(4)) }
    cancelOrder()
    eventually { cancelButton shouldBe 'disabled }
  }

  private def cancelButton = find[Button]("#cancelOrderBtn")
  private def ordersTable = findAll("#operations-table").last.asInstanceOf[TreeTableView[OperationProperties]]
  private def shownOrders = ordersTable.getRoot.getChildren.map(_.getValue)

  private def ordersTableRow(rowIndex: Int): TreeTableRow[OperationProperties] = {

    type RowType = TreeTableRow[OperationProperties]

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
          else if (children.get(0).isInstanceOf[TreeTableRow[_]]) toRowTypeSeq(children)
          else children.toArray(new Array[Node](0)).toSeq.flatMap(findTableRowChildren)
        case _ => Seq.empty
      }
    }

    findTableRowChildren(ordersTable)(rowIndex)
  }

  private def find(orderId: OrderId): Option[OperationProperties] =
    shownOrders.find(_.idProperty.value == orderId.value)

  trait OrderIsPresentFixture extends Fixture {
    val sampleOrder = Order(OrderId.random(), Bid, 1.BTC, Price(561.EUR))

    app.network.orders.set(sampleOrder.id, sampleOrder)
    eventually {
      find(sampleOrder.id) shouldBe 'defined
    }

    def cancelOrder(): Unit = {
      app.network.orders.set(sampleOrder.id, sampleOrder.cancel)
    }
  }
}
