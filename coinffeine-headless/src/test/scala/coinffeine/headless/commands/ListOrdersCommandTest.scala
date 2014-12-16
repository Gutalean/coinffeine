package coinffeine.headless.commands

import coinffeine.model.market._
import coinffeine.model.currency._
import coinffeine.model.properties.MutablePropertyMap

class ListOrdersCommandTest extends CommandTest {

  val orderMap = new MutablePropertyMap[OrderId, AnyCurrencyOrder]()
  val command = new ListOrdersCommand(orderMap)

  "The list orders command" should "print a message when no orders exist" in {
    executeCommand(command) should include("No orders")
  }

  it should "show order id, state and relevant amounts of existing orders" in {
    val order = Order.random(Bid, 10.BTC, Price(500.EUR))
    orderMap.set(order.id, order)
    executeCommand(command) should (
      include(order.id.value) and include(order.status.toString) and
        include(order.amount.toString) and include(order.price.toString))
  }

  it should "sort orders by the first field, the id" in {
    for (index <- 1 to 10) {
      val order = Order.random(Bid, index.BTC, Price((500 - index).EUR))
      orderMap.set(order.id, order)
    }
    val outputLines = executeCommand(command).lines.toList
    outputLines.sorted shouldBe outputLines
  }
}
