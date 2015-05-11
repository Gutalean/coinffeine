package coinffeine.headless.commands

import coinffeine.headless.prompt.ANSIText.Bold
import coinffeine.model.currency._
import coinffeine.model.order._
import coinffeine.model.properties.MutablePropertyMap

class ListOrdersCommandTest extends CommandTest {

  val orderMap = new MutablePropertyMap[OrderId, AnyCurrencyOrder]()
  val command = new ListOrdersCommand(orderMap)

  "The list orders command" should "print a message when no orders exist" in {
    executeCommand(command) should include("No orders")
  }

  it should "show order id, state and relevant amounts of existing orders" in {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(500.EUR))
    orderMap.set(order.id, order)
    executeCommand(command) should (
      include(order.id.value) and include(order.status.toString) and
        include(order.amount.toString) and include(order.price.toString))
  }

  it should "sort orders by type and the by id" in {
    val orders = for {
      index <- 1 to 10
      orderType <- OrderType.values
    } yield ActiveOrder.randomLimit(orderType, index.BTC, Price((500 - index).EUR))

    orders.foreach { order => orderMap.set(order.id, order) }

    val outputLines = executeCommand(command).lines.toList
    val (_, afterBidHeader) = outputLines.span(_ == Bold("Bid orders"))
    val (bids, _ :: asks) = afterBidHeader.span(_ != Bold("Ask orders"))
    bids.sorted shouldBe bids
    asks.sorted shouldBe asks
  }
}
