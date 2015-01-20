package coinffeine.headless.commands

import java.io.PrintWriter

import coinffeine.headless.parsing.{AmountsParser, Tokenizer}
import coinffeine.headless.prompt.ANSIText.Red
import coinffeine.headless.shell.Command
import coinffeine.model.currency.{Bitcoin, FiatCurrency}
import coinffeine.model.market._
import coinffeine.peer.api.CoinffeineNetwork

class OpenOrderCommand(orderType: OrderType, network: CoinffeineNetwork) extends Command {

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
      val order = Order.random(orderType, amount, price)
      network.submitOrder(order)
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
