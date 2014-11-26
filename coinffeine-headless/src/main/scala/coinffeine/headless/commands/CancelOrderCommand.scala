package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.parsing.Tokenizer
import coinffeine.headless.prompt.ANSIText.Red
import coinffeine.headless.shell.Command
import coinffeine.model.market.OrderId
import coinffeine.peer.api.CoinffeineNetwork

class CancelOrderCommand(network: CoinffeineNetwork) extends Command {
  override val keyword = "cancel"
  override val description = "cancels an order"
  override val usage =
    """cancel <order id>
      |
      |  where order id is an UUID similar to 0bb816cc-2acc-443e-a7e7-7b54a07e2d9c
    """.stripMargin

  override def apply(output: PrintWriter, args: String): Unit = {
    def reportError(message: String): Unit = {
      output.println(Red("ERROR: " + message))
    }
    def reportMissingId(): Unit = reportError("missing order id")
    def reportInvalidId(invalidId: String): Unit = reportError(s"invalid order id: '$invalidId'")

    def cancelOrder(id: OrderId): Unit = {
      if (network.orders.get(id).isEmpty) reportError("order not found")
      else network.cancelOrder(id, "user cancellation")
    }

    Tokenizer.splitWords(args) match {
      case Array() => reportMissingId()
      case Array(id) =>
        OrderId.parse(id).fold(reportInvalidId(id))(cancelOrder)
      case _ => reportInvalidId(args)
    }
  }
}
