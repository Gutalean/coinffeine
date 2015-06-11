package coinffeine.headless.commands

import java.io.PrintWriter
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import coinffeine.headless.parsing.{AmountsParser, Tokenizer}
import coinffeine.headless.prompt.ANSIText.Red
import coinffeine.headless.shell.Command
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.order.{LimitPrice, OrderRequest, OrderType, Price}
import coinffeine.peer.api.CoinffeineOperations

class OpenOrderCommand(orderType: OrderType, operations: CoinffeineOperations) extends Command {

  override val keyword = orderType.shortName
  override val description = "allows opening new orders"
  override val usage =
    s"""
       |$keyword <amount> <price>
       |
       |where:
       |  <amount>  Total bitcoin amount to exchange. Examples 0.05BTC, 0.25
       |  <price>   Price per BTC. Examples 434EUR/BTC, 10.355USD/BTC (the '/BTC' is optional)
     """.stripMargin

  override def apply(output: PrintWriter, args: String): Unit = {

    def reportInvalidArgs(): Unit = {
      output.println(Red(
        s"ERROR: a bitcoin amount and a price were expected, invalid arguments '$args' given"))
    }

    def openOrder(amount: Bitcoin.Amount, price: Price[_ <: FiatCurrency]): Unit = {
      val order = Await.result(
        operations.submitOrder(OrderRequest(orderType, amount, LimitPrice(price))), Duration.Inf)
      output.format("Created order %s%n", order.id.value)
    }

    parseArguments(args).fold(reportInvalidArgs())(openOrder _ tupled)
  }

  private def parseArguments(args: String): Option[(Bitcoin.Amount, Price[_ <: FiatCurrency])] =
    Tokenizer.splitWords(args) match {
      case Array(AmountsParser.BitcoinAmount(amount), AmountsParser.Price(price)) =>
        Some(amount -> price)
      case _ => None
    }
}
