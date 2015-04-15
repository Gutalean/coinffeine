package coinffeine.headless.commands

import org.joda.time.DateTime

import coinffeine.model.currency._
import coinffeine.model.exchange.{BuyerRole, Exchange, ExchangeId}
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.amounts.DefaultAmountsComponent

class ShowOrderDetailsCommandTest extends CommandTest with DefaultAmountsComponent {

  val network = new MockCoinffeineNetwork
  val command = new ShowOrderDetailsCommand(network)

  "The show order details command" should "require a well formed order id" in {
    executeCommand(command, "random text") should include("invalid order id")
  }

  it should "report information on existing orders" in {
    val orderCreation = DateTime.parse("2015-01-01T00:00")
    val exchange = Exchange.create(
      id = ExchangeId.random(),
      role = BuyerRole,
      counterpartId = PeerId.hashOf("counterpart"),
      amounts = amountsCalculator.exchangeAmountsFor(0.4.BTC, 50.EUR),
      parameters = Exchange.Parameters(lockTime = 1234, network = null),
      createdOn = orderCreation.plusMinutes(1)
    )
    val order = Order.randomLimit(Bid, 1.BTC, Price(100.EUR), orderCreation).withExchange(exchange)

    network.givenOrderExists(order)
    executeCommand(command, order.id.value) should (
      include(order.id.value) and include("Bid") and include(order.status.toString) and
       include(exchange.id.toString) and include("0%"))
  }
}
