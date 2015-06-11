package coinffeine.headless.commands

import scalaz.syntax.validation._

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.order.{ActiveOrder, Bid, OrderId, Price}

class OrderIdValidationTest extends UnitTest {

  val operations = new MockCoinffeineOperations
  val validator = new OrderIdValidation(operations)

  "Order id validation" should "fail for empty inputs" in {
    validator.requireExistingOrderId("") shouldBe "missing order id".failure
  }

  it should "fail for malformed order ids" in {
    validator.requireExistingOrderId("malformed") shouldBe "invalid order id: 'malformed'".failure
  }

  it should "fail for ids of non existing orders" in {
    val input = OrderId.random().value
    validator.requireExistingOrderId(input) shouldBe "order not found".failure
  }

  it should "accept well-formed ids of existing orders" in {
    val order = ActiveOrder.randomLimit(Bid, 1.BTC, Price(1.EUR))
    operations.givenOrderExists(order)
    val input = order.id.value
    validator.requireExistingOrderId(input) shouldBe order.id.success
  }
}
