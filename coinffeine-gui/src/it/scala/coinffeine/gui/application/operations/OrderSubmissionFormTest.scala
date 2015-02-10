package coinffeine.gui.application.operations

import javafx.scene.Node
import scala.concurrent.duration._
import scalafx.scene.layout.Pane

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.gui.GuiTest
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.api.impl.MockCoinffeineApp

class OrderSubmissionFormTest extends GuiTest[Pane] with Eventually {

  val app = new MockCoinffeineApp

  override def createRootNode(): Pane = new OrderSubmissionForm(app, DummyOrderValidation).root

  "The order submission form" should "forbid submission if the BTC amount is zero" in new Fixture {
    doubleClick("#limit").`type`("100")
    doubleClick("#amount").`type`("0.00")
    find[Node]("#submit") should be ('disabled)
  }

  it should "forbid to submit if the bitcoin value is invalid" in new Fixture {
    doubleClick("#amount").`type`("0.000000000000001")
    find[Node]("#submit") should be ('disabled)
  }

  it should "forbid submission if the limit value is zero" in new Fixture {
    doubleClick("#amount").`type`("0.1")
    find[Node]("#submit") should be ('disabled)
  }

  it should "forbid submission if the limit value is invalid" in new Fixture {
    doubleClick("#amount").`type`("0.5")
    doubleClick("#limit").`type`("0.001")
    find[Node]("#submit") should be ('disabled)
  }

  it should "provide the form's data upon submission" in new Fixture {
    doubleClick("#amount").`type`("0.1")
    doubleClick("#limit").`type`("100")
    find[Node]("#submit") should not be 'disabled

    click("#submit")

    val expectedAmount = 0.1.BTC
    val expectedPrice = LimitPrice(Price(100.EUR))
    eventually(Timeout(2.seconds)) {
      app.network.orders.values.toList.collect {
        case Order(_, Bid, `expectedAmount`, `expectedPrice`, _, _, _, _) =>
      } shouldBe 'nonEmpty
    }
  }
}
