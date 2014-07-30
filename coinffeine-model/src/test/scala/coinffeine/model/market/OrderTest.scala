package coinffeine.model.market

import com.google.bitcoin.params.MainNetParams

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Exchange.Progress
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId

class OrderTest extends UnitTest {

  val exchangeParameters = Exchange.Parameters(10, MainNetParams.get())

  "Order" must "report no progress with no exchanges" in {
    val order = Order(OrderId.random(), Bid, 1.BTC, 600.EUR)
    order.progress should be (0.0)
  }

  it must "report progress with one incomplete exchange" in {
    val order = Order(OrderId.random(), Bid, 1.BTC, 600.EUR)
      .withExchange(createExchange(0.5))
    order.progress should be (0.5)
  }

  it must "report progress with one incomplete exchange that overwrites itself" in {
    val order = Order(OrderId.random(), Bid, 1.BTC, 600.EUR)
      .withExchange(createExchange(0.5))
      .withExchange(createExchange(0.6))
    order.progress should be (0.6)
  }

  private def createExchange(completion: Double) = new Exchange[FiatCurrency] {
    override val id = ExchangeId("exchange")
    override val role = BuyerRole
    override val counterpartId = PeerId("seller")
    override val parameters = exchangeParameters
    override val brokerId = PeerId("broker")
    override val amounts = Exchange.Amounts(1.BTC, 600.EUR, Exchange.StepBreakdown(10))
    override val progress = Progress[FiatCurrency](1.BTC * completion, 600.EUR * completion)
  }
}
