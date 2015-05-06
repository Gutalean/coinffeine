package coinffeine.headless.commands

import org.scalatest.Inside

import coinffeine.model.currency._
import coinffeine.model.market._

class OpenOrderCommandTest extends CommandTest with Inside {

  "The open order command" should "reject invalid arguments" in new Fixture {
    executeCommand(bidCommand, "") should include ("invalid arguments")
    executeCommand(bidCommand, "10BTC") should include ("invalid arguments")
    executeCommand(bidCommand, "10BTC 50 euros") should include ("invalid arguments")
    executeCommand(bidCommand, "10BTC 50EUR garbage") should include ("invalid arguments")
  }

  it should "open a bid order" in new Fixture {
    val output = executeCommand(bidCommand, "10.00BTC 50EUR")
    val order = network.submissions.head
    order.orderType shouldBe Bid
    order.amount shouldBe 10.BTC
    order.price shouldBe LimitPrice(Price(50.EUR))
    output should include(s"Created order ${order.id.value}")
  }

  it should "open an ask order" in new Fixture {
    val output = executeCommand(askCommand, "10.00BTC 50EUR")
    network.submissions.head.orderType shouldBe Ask
  }

  trait Fixture {
    protected val network = new CoinffeineNetworkSpy
    protected val bidCommand = new OpenOrderCommand(Bid, network)
    protected val askCommand = new OpenOrderCommand(Ask, network)
  }

  class CoinffeineNetworkSpy extends DummyCoinffeineNetwork {
    private var _submissions = Seq.empty[AnyCurrencyActiveOrder]

    def submissions: Seq[AnyCurrencyActiveOrder] = _submissions

    override def submitOrder[C <: FiatCurrency](request: OrderRequest[C]) = synchronized {
      val order = request.create()
      _submissions :+= order
      order
    }
  }
}
