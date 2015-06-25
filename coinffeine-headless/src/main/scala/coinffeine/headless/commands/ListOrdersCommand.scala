package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.common.properties.PropertyMap
import coinffeine.headless.prompt.ANSIText.Bold
import coinffeine.headless.shell.Command
import coinffeine.model.order.{Order, OrderId, OrderType}

class ListOrdersCommand(ordersProperty: PropertyMap[OrderId, Order]) extends Command {
  override val keyword = "list"
  override val description = "lists all orders"

  override def apply(output: PrintWriter, args: String): Unit = {
    val currentOrders = ordersProperty.content.map(_._2).toList
    if (currentOrders.isEmpty) output.println("No orders")
    else printOrders(output, currentOrders)
  }

  def printOrders(output: PrintWriter, allOrders: List[Order]): Unit = {
    val ordersByType = allOrders.groupBy(_.orderType)
    for {
      orderType <- OrderType.values
      orders <- ordersByType.get(orderType)
    } {
      printOrders(output, orderType, orders)
    }
  }

  private def printOrders(output: PrintWriter,
                          orderType: OrderType,
                          orders: List[Order]): Unit = {
    output.println(Bold(orderType.shortName.capitalize + " orders"))
    orders.sortBy(_.id.value).foreach { order =>
      output.format("%s\t%s\t%s\t%s%n", order.id.value, order.status, order.amount, order.price)
    }
  }
}
