package coinffeine.peer.market

import scala.concurrent.duration._

import akka.actor.Props

import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.market.{Ask, Bid, OrderBookEntry, OrderId}
import coinffeine.model.network.PeerId
import coinffeine.peer.market.SubmissionSupervisor.{KeepSubmitting, StopSubmitting}
import coinffeine.protocol.gateway.GatewayProbe
import coinffeine.protocol.messages.brokerage.{Market, PeerOrderRequests}
import com.coinffeine.common.ProtocolConstants
import com.coinffeine.common.test.AkkaSpec

class SubmissionSupervisorTest extends AkkaSpec {

  val constants = ProtocolConstants.DefaultConstants.copy(
    orderExpirationInterval = 6.seconds,
    orderResubmitInterval = 4.seconds
  )
  val brokerId = PeerId("broker")
  val eurOrder1 = OrderBookEntry(OrderId("eurOrder1"), Bid, 1.3.BTC, 556.EUR)
  val eurOrder2 = OrderBookEntry(OrderId("eurOrder2"), Ask, 0.7.BTC, 640.EUR)
  val usdOrder = OrderBookEntry(OrderId("usdOrder"), Ask, 0.5.BTC, 500.USD)
  val noEurOrders = PeerOrderRequests.empty(Market(Euro))
  val firstEurOrder = noEurOrders.addEntry(eurOrder1)
  val bothEurOrders = firstEurOrder.addEntry(eurOrder2)

  trait Fixture {
    val gateway = new GatewayProbe()
    val actor = system.actorOf(Props(new SubmissionSupervisor(constants)))
    actor ! SubmissionSupervisor.Initialize(brokerId, gateway.ref)
  }

  "An order submission actor" must "keep silent as long as there is no open orders" in new Fixture {
    gateway.expectNoMsg()
  }

  it must "submit all orders as soon as a new one is open" in new Fixture {
    actor ! KeepSubmitting(eurOrder1)
    gateway.expectForwarding(firstEurOrder, brokerId)
    actor ! KeepSubmitting(eurOrder2)
    gateway.expectForwarding(bothEurOrders, brokerId)
  }

  it must "keep resubmitting open orders to avoid them being discarded" in new Fixture {
    actor ! KeepSubmitting(eurOrder1)
    gateway.expectForwarding(firstEurOrder, brokerId)
    gateway.expectForwarding(firstEurOrder, brokerId, timeout = constants.orderExpirationInterval)
    gateway.expectForwarding(firstEurOrder, brokerId, timeout = constants.orderExpirationInterval)
  }

  it must "group orders by target market" in new Fixture {
    actor ! KeepSubmitting(eurOrder1)
    actor ! KeepSubmitting(usdOrder)

    def currencyOfNextOrderSet(): FiatCurrency =
      gateway.expectForwardingPF(brokerId, constants.orderExpirationInterval) {
        case PeerOrderRequests(Market(currency), _) => currency
      }

    val currencies = Set(currencyOfNextOrderSet(), currencyOfNextOrderSet())
    currencies should be (Set(Euro, UsDollar))
  }

  it must "keep resubmitting remaining orders after a cancellation" in new Fixture {
    actor ! KeepSubmitting(eurOrder1)
    gateway.expectForwarding(firstEurOrder, brokerId)
    actor ! KeepSubmitting(eurOrder2)
    gateway.expectForwarding(bothEurOrders, brokerId)
    actor ! StopSubmitting(eurOrder2.id)
    gateway.expectForwarding(firstEurOrder, brokerId)
    gateway.expectForwarding(firstEurOrder, brokerId, timeout = constants.orderExpirationInterval)
    gateway.expectForwarding(firstEurOrder, brokerId, timeout = constants.orderExpirationInterval)
  }

  it must "keep silent if all the orders get cancelled" in new Fixture {
    actor ! KeepSubmitting(eurOrder1)
    gateway.expectForwarding(firstEurOrder, brokerId)
    actor ! StopSubmitting(eurOrder1.id)
    gateway.expectForwarding(noEurOrders, brokerId)
    gateway.expectNoMsg(constants.orderExpirationInterval)
  }
}
