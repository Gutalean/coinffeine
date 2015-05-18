package coinffeine.peer.market.orders

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor.{ActorContext, ActorRef, Props}
import akka.testkit._
import akka.pattern._
import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.Both
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange.ActiveExchange.DepositAmounts
import coinffeine.model.exchange.Exchange.Progress
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.MutableCoinffeineNetworkProperties
import coinffeine.model.order._
import coinffeine.peer.amounts.AmountsCalculatorStub
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.peer.market.orders.OrderActor.Delegates
import coinffeine.peer.market.orders.controller.OrderController
import coinffeine.peer.market.orders.funds.FundsBlockerActor
import coinffeine.peer.market.submission.SubmissionSupervisor.{InMarket, KeepSubmitting}
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

abstract class OrderActorTest extends AkkaSpec
    with SampleExchange with BuyerPerspective with CoinffeineUnitTestNetwork.Component
    with Inside with Eventually {

  protected val idleTime = 100.millis.dilated
  private implicit val patience = PatienceConfig(idleTime * 10, idleTime)

  protected trait Fixture {
    val order = ActiveOrder.randomLimit(Bid, 10.BTC, Price(2.EUR))
    val orderMatch = OrderMatch(
      order.id,
      exchangeId,
      Both(buyer = amounts.netBitcoinExchanged, seller = amounts.grossBitcoinExchanged),
      Both(buyer = amounts.grossFiatExchanged, seller = amounts.netFiatExchanged),
      lockTime = 400000L,
      exchange.counterpartId
    )
    val halfOrderAmounts = ActiveExchange.Amounts(
      grossBitcoinExchanged = 5.006.BTC,
      grossFiatExchanged = 5.25.EUR,
      deposits = Both(
        buyer = DepositAmounts(input = 2.002.BTC, output = 2.BTC),
        seller = DepositAmounts(input = 11.006.BTC, output = 11.004.BTC)
      ),
      refunds = Both(buyer = 1.BTC, seller = 5.BTC),
      intermediateSteps = Seq.tabulate(5) { index =>
        val step = index + 1
        ActiveExchange.IntermediateStepAmounts(
          depositSplit = Both(buyer = 1.BTC * step + 0.002.BTC, seller = 10.BTC - 1.BTC * step),
          fiatAmount = 1.EUR,
          fiatFee = 0.05.EUR,
          progress = Progress(Both(buyer = 1.BTC * step, seller = 1.BTC * step + 0.006.BTC))
        )
      },
      finalStep = ActiveExchange.FinalStepAmounts(
        depositSplit = Both(buyer = 7.002.BTC, seller = 1.BTC),
        progress = Progress(Both(buyer = 5.BTC, seller = 5.006.BTC))
      )
    )
    val halfOrderMatch = orderMatch.copy(
      exchangeId = ExchangeId.random(),
      bitcoinAmount = Both(
        buyer = halfOrderAmounts.netBitcoinExchanged,
        seller = halfOrderAmounts.grossBitcoinExchanged
      ),
      fiatAmount = Both(
        buyer = halfOrderAmounts.grossFiatExchanged,
        seller = halfOrderAmounts.netFiatExchanged
      )
    )
    val gatewayProbe = new MockGateway()
    val fundsBlocker, exchangeActor = new MockSupervisedActor()
    val submissionProbe, paymentProcessorProbe, bitcoinPeerProbe, blockchainProbe,
      walletProbe, archiveProbe = TestProbe()
    val entry = OrderBookEntry.fromOrder(order)
    private val calculatorStub = new AmountsCalculatorStub(amounts, halfOrderAmounts)
    val properties = new MutableCoinffeineNetworkProperties
    private val props = Props(new OrderActor[Euro.type](
      order,
      new OrderController(myRole.select(peerIds), calculatorStub, network, order),
      new Delegates[Euro.type] {
        override def exchangeActor(exchange: HandshakingExchange[Euro.type])
                                  (implicit context: ActorContext) =
          Fixture.this.exchangeActor.props(exchange)

        override def fundsBlocker(id: ExchangeId, funds: RequiredFunds[Euro.type])
                                 (implicit context: ActorContext): Props =
          Fixture.this.fundsBlocker.props(id)
      },
      properties,
      OrderActor.Collaborators(walletProbe.ref, paymentProcessorProbe.ref,
        submissionProbe.ref, gatewayProbe.ref, bitcoinPeerProbe.ref, blockchainProbe.ref,
        archiveProbe.ref)
    ))
    var actor: ActorRef = _

    def startOrder(): Unit = {
      actor = system.actorOf(props)
      watch(actor)
    }

    def restartOrder(): Unit = {
      system.stop(actor)
      expectTerminated(actor)
      startOrder()
    }

    def givenInitializedOrder(): Unit = {
      startOrder()
      eventually { properties.orders.get(order.id) shouldBe 'defined }
    }

    def givenOfflineOrder(): Unit = {
      givenInitializedOrder()
      expectProperty { _ should not be 'inMarket }
      submissionProbe.expectMsg(KeepSubmitting(entry))
    }

    def givenInMarketOrder(): Unit = {
      givenOfflineOrder()
      submissionProbe.send(actor, InMarket(entry))
      expectProperty { _.inMarket shouldBe true }
    }

    def shouldRejectAnOrderMatch(cause: ExchangeRejection.Cause): Unit = {
      val otherExchangeId = ExchangeId.random()
      gatewayProbe.relayMessageFromBroker(orderMatch.copy(exchangeId = otherExchangeId))
      gatewayProbe.expectForwardingToBroker(ExchangeRejection(otherExchangeId, cause))
    }

    def givenSuccessfulFundsBlocking(exchangeId: ExchangeId): Unit = {
      fundsBlocker.expectCreation()
      fundsBlocker.probe.send(actor, FundsBlockerActor.BlockingResult(exchangeId, Success {}))
    }

    def givenFailedFundsBlocking(): Unit = {
      fundsBlocker.expectCreation()
      fundsBlocker.probe.send(actor, FundsBlockerActor.BlockingResult(exchangeId,
        Failure(new Exception("intended lack of funds"))))
    }

    def givenASuccessfulPerfectMatchExchange(): Unit = {
      givenInMarketOrder()
      gatewayProbe.relayMessageFromBroker(orderMatch)
      givenSuccessfulFundsBlocking(orderMatch.exchangeId)
      exchangeActor.probe.send(actor,
        ExchangeActor.ExchangeSuccess(completedExchange.copy(timestamp = DateTime.now())))
    }

    def expectProperty(f: AnyCurrencyOrder => Unit): Unit = {
      eventually(timeout = Timeout(3.seconds.dilated)) {
        f(properties.orders(order.id))
      }
    }

    def expectAlive(ref: ActorRef, after: FiniteDuration = 500.millis.dilated): Unit = {
      import system.dispatcher
      Thread.sleep(after.toMillis)
      val lookupTimeout = 1.second.dilated
      system.actorSelection(ref.path).resolveOne(lookupTimeout).pipeTo(self)
      expectMsg(lookupTimeout, "Actor is not alive", ref)
    }
  }
}
