package coinffeine.headless.commands

import scalaz.Scalaz

import coinffeine.common.test.UnitTest
import coinffeine.model.market._
import coinffeine.model.currency._

class OrderIdValidationTest extends UnitTest {
  import Scalaz._

  val network = new MockCoinffeineNetwork
  val validator = new OrderIdValidation(network)

  "Order id validation" should "fail for empty inputs" in {
    validator.requireExistingOrderId("") shouldBe "missing order id".fail
  }

  it should "fail for malformed order ids" in {
    validator.requireExistingOrderId("malformed") shouldBe "invalid order id: 'malformed'".fail
  }

  it should "fail for ids of non existing orders" in {
    val input = OrderId.random().value
    validator.requireExistingOrderId(input) shouldBe "order not found".fail
  }

  it should "accept well-formed ids of existing orders" in {
    val order = Order.randomLimit(Bid, 1.BTC, Price(1.EUR))
    network.givenOrderExists(order)
    val input = order.id.value
    validator.requireExistingOrderId(input) shouldBe order.id.success
  }
}
