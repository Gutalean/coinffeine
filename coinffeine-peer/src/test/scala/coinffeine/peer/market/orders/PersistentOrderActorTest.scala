package coinffeine.peer.market.orders

import coinffeine.common.akka.test.MockActor.MockStopped
import coinffeine.model.currency.Euro
import coinffeine.model.exchange.NonStartedExchange
import coinffeine.protocol.messages.handshake.ExchangeRejection

class PersistentOrderActorTest extends OrderActorTest {

  "A persistent order actor" should "remember order matches that were being accepted" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    fundsBlocker.expectCreation()
    expectNoMsg(idleTime)

    restartOrder()
    expectNoMsg(idleTime)
    shouldRejectAnOrderMatch("Accepting other match")
  }

  it should "remember that a match couldn't start because of funds shortage" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenFailedFundsBlocking()
    gatewayProbe.expectForwardingToBroker(
      ExchangeRejection(orderMatch.exchangeId, "Cannot block funds"))

    restartOrder()
    fundsBlocker.expectStop()

    expectNoMsg(idleTime)
    gatewayProbe.relayMessageFromBroker(orderMatch)
    fundsBlocker.expectCreation()
  }

  it should "remember that an exchange was started" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenSuccessfulFundsBlocking()
    val Seq(exchange: NonStartedExchange[Euro.type]) = exchangeActor.expectCreation()
    exchange.id shouldBe orderMatch.exchangeId

    restartOrder()
    exchangeActor.probe.expectMsgType[MockStopped]
    exchangeActor.expectCreation() shouldBe Seq(exchange)
  }
}
