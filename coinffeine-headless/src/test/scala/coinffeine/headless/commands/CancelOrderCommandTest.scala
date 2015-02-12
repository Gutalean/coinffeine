package coinffeine.headless.commands

import coinffeine.model.currency._
import coinffeine.model.market._

class CancelOrderCommandTest extends CommandTest {

  "The cancel order command" should "require a well-formed order id" in new Fixture {
    executeCommand(command, "random text") should include("invalid order id")
  }

  it should "cancel existing orders" in new Fixture {
    val order = Order.randomLimit(Bid, 10.BTC, Price(10.EUR))
    network.givenOrderExists(order)
    executeCommand(command, order.id.value.toString)
    network.cancellations shouldBe Seq(order.id)
  }

  private trait Fixture {
    val network = new MockCoinffeineNetwork
    val command = new CancelOrderCommand(network)
  }
}
