package coinffeine.headless.commands

import coinffeine.model.currency._
import coinffeine.model.order.{ActiveOrder, Bid, Price}

class CancelOrderCommandTest extends CommandTest {

  "The cancel order command" should "require a well-formed order id" in new Fixture {
    executeCommand(command, "random text") should include("invalid order id")
  }

  it should "cancel existing orders" in new Fixture {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(10.EUR))
    operations.givenOrderExists(order)
    executeCommand(command, order.id.value.toString)
    operations.cancellations shouldBe Seq(order.id)
  }

  private trait Fixture {
    val operations = new MockCoinffeineOperations
    val command = new CancelOrderCommand(operations)
  }
}
