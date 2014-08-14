package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{BlockedCoinsId, ImmutableTransaction, KeyPair, MutableTransaction}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.model.network.PeerId

class OrderTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

  val exchangeParameters = Exchange.Parameters(10, network)
  val dummyDeposits =
    Exchange.Deposits(Both.fill(ImmutableTransaction(new MutableTransaction(network))))

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

  private def createExchange(completion: Double) = Exchange.nonStarted(
    id = ExchangeId("exchange"),
    role = BuyerRole,
    counterpartId = PeerId("seller"),
    parameters = exchangeParameters,
    brokerId = PeerId("broker"),
    amounts = Exchange.Amounts(1.BTC, 600.EUR, Exchange.StepBreakdown(10)),
    blockedFunds = Exchange.BlockedFunds(fiat = None, bitcoin = BlockedCoinsId(42))
  ).startHandshaking(
    user = PeerInfo("account1", new KeyPair()),
    counterpart = PeerInfo("account2", new KeyPair())
  ).startExchanging(dummyDeposits)
    .increaseProgress(btcAmount = 1.BTC * completion, fiatAmount = 600.EUR * completion)
}
