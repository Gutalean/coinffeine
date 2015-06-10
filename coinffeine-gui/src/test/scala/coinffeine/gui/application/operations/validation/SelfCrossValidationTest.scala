package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import org.scalatest.Inside

import coinffeine.common.properties.MutablePropertyMap
import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.order._

class SelfCrossValidationTest extends UnitTest with Inside {

  private val request = OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR))

  "Self-cross requirement" should "avoid self-crossing" in new Fixture {
    val crossingAsk = ActiveOrder.randomLimit(Ask, 0.03.BTC, Price(295.EUR))
    orders.set(crossingAsk.id, crossingAsk)

    inside(instance.apply(request, Spread.empty)) {
      case Error(NonEmptyList(unmetRequirement)) =>
        unmetRequirement should include ("self-cross")
    }
  }

  it should "accept not self-crossing orders" in new Fixture {
    instance.apply(request, Spread.empty) shouldBe OK
  }

  private trait Fixture {
    val orders = new MutablePropertyMap[OrderId, AnyCurrencyOrder]
    val existingOrder = ActiveOrder.randomLimit(Bid, 1.BTC, Price(200.EUR))
    orders.set(existingOrder.id, existingOrder)
    val instance = new SelfCrossValidation(orders)
  }
}
