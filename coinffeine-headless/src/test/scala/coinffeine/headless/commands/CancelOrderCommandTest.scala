package coinffeine.headless.commands

import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.properties.MutablePropertyMap

class CancelOrderCommandTest extends CommandTest {

  "The cancel order command" should "require a well-formed order id" in new Fixture {
    executeCommand(command) should include("missing order id")
    executeCommand(command, "45") should include("invalid order id")
    executeCommand(command, "random text") should include("invalid order id")
  }

  it should "fail for non-existing orders" in new Fixture {
    val order = Order(Bid, 10.BTC, Price(10.EUR))
    executeCommand(command, order.id.value.toString) should include("order not found")
  }

  it should "cancel existing orders" in new Fixture {
    val order = Order(Bid, 10.BTC, Price(10.EUR))
    network.givenOrderExists(order)
    executeCommand(command, order.id.value.toString)
    network.cancellations shouldBe Seq(order.id)
  }

  private trait Fixture {
    val network = new MockCoinffeineNetwork
    val command = new CancelOrderCommand(network)
  }

  private class MockCoinffeineNetwork extends DummyCoinffeineNetwork {

    override val orders = new MutablePropertyMap[OrderId, AnyCurrencyOrder]

    def givenOrderExists(order: Order[Euro.type]): Unit = {
      orders.set(order.id, order)
    }

    private var _cancellations = Seq.empty[OrderId]

    def cancellations: Seq[OrderId] = _cancellations

    override def cancelOrder(order: OrderId): Unit = synchronized { _cancellations :+= order }
  }
}
