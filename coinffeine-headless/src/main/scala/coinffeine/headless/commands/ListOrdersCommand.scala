package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.shell.Command
import coinffeine.model.market._
import coinffeine.model.properties.PropertyMap

class ListOrdersCommand(ordersProperty: PropertyMap[OrderId, AnyCurrencyOrder]) extends Command {
  override val keyword = "list-orders"
  override val description = "lists all orders"

  override def apply(output: PrintWriter, args: String): Unit = {
    val currentOrders = ordersProperty.content.map(_._2).toList.sortBy(_.id.value)
    if (currentOrders.isEmpty) output.println("No orders")
    else printOrders(output, currentOrders)
  }

  def printOrders(output: PrintWriter, orders: List[AnyCurrencyOrder]): Unit = {
    orders.foreach { order =>
      output.format("%s\t%s\t%s\t%s%n", order.id.value, order.status, order.amount, order.price)
    }
  }
}
