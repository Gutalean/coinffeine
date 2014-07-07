package com.coinffeine.gui.application.operations

import javafx.scene.Node
import org.scalatest.concurrent.Eventually
import scalafx.scene.layout.Pane

import com.coinffeine.client.api.MockCoinffeineApp
import com.coinffeine.common.{Bid, Order}
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.gui.GuiTest

class OrderSubmissionFormTest extends GuiTest[Pane] with Eventually {

  val app = new MockCoinffeineApp

  override def createRootNode(): Pane = {
    new OperationsView(app).centerPane
    new OrderSubmissionForm(app).root
  }

  "The operations view" should "not let the user submit if the bitcoin amount is zero" in new Fixture {
    doubleClick("#limit").`type`("100")
    doubleClick("#amount").`type`("0.00")
    find[Node]("#submit") should be ('disabled)
  }

  it should "not let the user submit if the bitcoin value is invalid" in new Fixture {
    doubleClick("#amount").`type`("0.000000000000001")
    find[Node]("#submit") should be ('disabled)
  }

  it should "not let the user submit if the limit value is zero" in new Fixture {
    doubleClick("#amount").`type`("0.1")
    find[Node]("#submit") should be ('disabled)
  }

  it should "not let the user submit if the limit value is invalid" in new Fixture {
    doubleClick("#amount").`type`("0.5")
    doubleClick("#limit").`type`("0.001")
    find[Node]("#submit") should be ('disabled)
  }

  it should "provide the form's data upon submission" in new Fixture {
    doubleClick("#amount").`type`("0.1")
    doubleClick("#limit").`type`("100")
    find[Node]("#submit") should not be ('disabled)

    click("#submit")

    val expectedOrder = Order(orderType = Bid, amount = 0.1.BTC, price = 100.EUR)
    app.network.orders should contain(expectedOrder)
  }
}
