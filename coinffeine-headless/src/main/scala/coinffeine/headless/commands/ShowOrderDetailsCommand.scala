package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.prompt.ANSIText._
import coinffeine.headless.shell.Command
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.exchange.Exchange.Amounts
import coinffeine.model.order.{OrderId, AnyCurrencyOrder}
import coinffeine.peer.api.CoinffeineNetwork

class ShowOrderDetailsCommand(network: CoinffeineNetwork) extends Command {
  override val keyword = "show"
  override val description = "print order details"
  override val usage =
    """show <order id>
      |
      |  where order id is an UUID similar to 0bb816cc-2acc-443e-a7e7-7b54a07e2d9c
    """.stripMargin

  private val validator = new OrderIdValidation(network)

  override def apply(output: PrintWriter, args: String): Unit = {
    def reportError(message: String): Unit = {
      output.println(Red("ERROR: " + message))
    }

    def printOrderDetails(id: OrderId): Unit = {
      val order = network.orders(id)
      new OrderDetailsPrinter(output, order).print()
    }

    validator.requireExistingOrderId(args).fold(fail = reportError, succ = printOrderDetails)
  }

  private class OrderDetailsPrinter(output: PrintWriter, order: AnyCurrencyOrder) {

    def print(): Unit = {
      printGeneralDetails()
      printExchangesDetails()
    }

    private def printGeneralDetails(): Unit = {
      output.format("%s %s exchange of %s at %s%n",
        Bold(s"Order ${order.id.value}:"), order.orderType, order.amount, order.price)
      printStatus(order.status)
      printProgress(order.progress)
    }

    private def printExchangesDetails(): Unit = {
      if (order.exchanges.isEmpty) output.println(Bold("Exchanges:") + " none")
      else {
        output.println(Bold("Exchanges:"))
        order.exchanges.values.foreach(printExchangeDetails)
      }
    }

    private def printExchangeDetails(exchange: Exchange[_ <: FiatCurrency]): Unit = {
      output.println(Bold("\t" + exchange.id))
      printExchangeAmounts(exchange.amounts)
      printStatus(exchange.status.name)
      printProgress((exchange.progress.bitcoinsTransferred(order.orderType).value /
        exchange.amounts.exchangedBitcoin(order.orderType).value).doubleValue())
    }

    private def printExchangeAmounts(amounts: Amounts[_ <: FiatCurrency]): Unit = {
      output.format("\tAmounts: %s (%s net) by %s (%s net)%n",
        amounts.exchangedBitcoin.seller, amounts.exchangedBitcoin.buyer,
        amounts.exchangedFiat.buyer, amounts.exchangedFiat.seller
      )
    }

    private def printStatus(status: Any): Unit = {
      output.println("\tStatus: " + status)
    }

    def printProgress(progress: Double): Unit = {
      output.println("\tProgress: %s %5.2f%%".format(progressBar(40, progress), progress * 100))
    }

    private def progressBar(columns: Int, progress: Double): String =
      Seq.tabulate(columns) { index => if ((index + 1) > columns * progress) "." else "=" }.mkString("")
  }
}
