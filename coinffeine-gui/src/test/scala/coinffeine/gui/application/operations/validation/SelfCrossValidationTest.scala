package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.properties.MutablePropertyMap

class SelfCrossValidationTest extends UnitTest with Inside {

  private val newBid = OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR))

  "Self-cross requirement" should "avoid self-crossing" in new Fixture {
    val crossingAsk = Order.randomLimit(Ask, 0.03.BTC, Price(295.EUR))
    orders.set(crossingAsk.id, crossingAsk)

    inside(instance.apply(newBid)) {
      case Error(NonEmptyList(unmetRequirement)) =>
        unmetRequirement.description should include ("self-cross")
    }
  }

  it should "accept not self-crossing orders" in new Fixture {
    instance.apply(newBid) shouldBe OK
  }

  private trait Fixture {
    val orders = new MutablePropertyMap[OrderId, AnyCurrencyOrder]
    val existingOrder = Order.randomLimit(Bid, 1.BTC, Price(200.EUR))
    orders.set(existingOrder.id, existingOrder)
    val instance = new SelfCrossValidation(orders)
  }
}
