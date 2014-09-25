package coinffeine.peer.market.orders

import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.{MatchResult, Matcher}

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{BlockedCoinsId, KeyPair}
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.{MutableCoinffeineNetworkProperties, PeerId}
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.amounts.AmountsCalculatorStub
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.exchange.ExchangeActor.ExchangeToStart
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.peer.market.orders.OrderActor.Delegates
import coinffeine.peer.market.orders.controller.OrderController
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MockGateway
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.messages.handshake.ExchangeRejection

class OrderActorTest extends AkkaSpec
    with SampleExchange with BuyerPerspective with CoinffeineUnitTestNetwork.Component
    with Inside  with Eventually {

  val idleTime = 500.millis
  val order = Order(Bid, 5.BTC, Price(500.EUR))
  val orderMatch = OrderMatch(order.id, exchangeId, Both.fill(order.amount),
    Both.fill(order.price.of(order.amount)), lockTime = 400000L, exchange.counterpartId)

  "An order actor" should "keep order info" in new Fixture {
    actor ! OrderActor.RetrieveStatus
    expectMsgType[Order[_]]
  }

  it should "block FIAT funds plus fees when initialized" in new Fixture {
    givenInitializedOrder()
  }

  it should "keep in stalled status when there are not enough funds when buying" in new Fixture {
    givenInitializedOrder()
    givenFundsBecomeUnavailable()
    submissionProbe.expectNoMsg(idleTime)
  }

  it should "move to stalled when payment processor reports unavailable funds" in new Fixture {
    givenOfflineOrder()
    givenFundsBecomeUnavailable()
    submissionProbe.expectMsgType[StopSubmitting]
    expectProperty { _.status should beStalled }
  }

  it should "reject order matches when stalled" in new Fixture {
    givenOfflineOrder()
    givenFundsBecomeUnavailable()
    submissionProbe.expectMsgType[StopSubmitting]
    shouldRejectAnOrderMatch("No funds available")
  }

  it should "move to offline when receive available funds" in new Fixture {
    givenStalledOrder()
    givenFundsBecomeAvailable()
    expectProperty { _.status shouldBe OfflineOrder }
    submissionProbe.expectMsg(KeepSubmitting(entry))
  }

  it should "submit to the broker and receive submission status" in new Fixture {
    givenOfflineOrder()
    submissionProbe.send(actor, InMarket(entry))
    expectProperty { _.status shouldBe InMarketOrder }
  }

  it should "keep submitting to the broker until been cancelled" in new Fixture {
    givenOfflineOrder()
    expectNoMsg(idleTime)
    val reason = "some reason"
    actor ! OrderActor.CancelOrder(reason)
    submissionProbe.expectMsg(StopSubmitting(order.id))
    expectProperty { _.status should beCancelled }
  }

  it should "reject order matches after being cancelled" in new Fixture {
    givenOfflineOrder()
    actor ! OrderActor.CancelOrder("got bored")
    shouldRejectAnOrderMatch("Order already finished")
  }

  it should "release funds when being cancelled" in new Fixture {
    givenOfflineOrder()
    actor ! OrderActor.CancelOrder("testing purposes")
    submissionProbe.expectMsgType[StopSubmitting]
    fundsActor.expectMsg(OrderFundsActor.UnblockFunds)
  }

  it should "move from stalled to offline when available funds message is received" in new Fixture {
    givenStalledOrder()
    givenFundsBecomeAvailable()
    expectProperty { _.status shouldBe OfflineOrder }
    submissionProbe.expectMsg(KeepSubmitting(entry))
  }

  it should "stop submitting to the broker & report new status once matching is received" in
    new Fixture {
      givenInMarketOrder()
      gatewayProbe.relayMessageFromBroker(orderMatch)
      submissionProbe.fishForMessage() {
        case StopSubmitting(orderId) if orderId == order.id => true
        case _ => false
      }
      expectProperty { _.status shouldBe InProgressOrder }
      expectProperty { _.progress shouldBe 0.0 }
      exchangeActor.probe.send(actor, ExchangeActor.ExchangeSuccess(completedExchange))
      expectProperty { _.status shouldBe CompletedOrder }
    }

  it should "spawn an exchange upon matching" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    val keyPair = givenAFreshKeyIsGenerated()
    givenPaymentProcessorAccountIsRetrieved()
    exchangeActor.expectCreation()
  }

  it should "reject new order matches if an exchange is active" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenAFreshKeyIsGenerated()
    givenPaymentProcessorAccountIsRetrieved()
    exchangeActor.expectCreation()
    shouldRejectAnOrderMatch("Exchange already in progress")
  }

  it should "not reject resubmissions of already accepted order matches" in new Fixture {
    givenInMarketOrder()
    gatewayProbe.relayMessageFromBroker(orderMatch)
    givenAFreshKeyIsGenerated()
    givenPaymentProcessorAccountIsRetrieved()
    exchangeActor.expectCreation()

    gatewayProbe.relayMessageFromBroker(orderMatch)
    gatewayProbe.expectNoMsg(idleTime)
  }

  it should "release remaining funds after completing exchanges" in new Fixture {
    givenInMarketOrder()
    givenASuccessfulPerfectMatchExchange()
    fundsActor.expectMsg(OrderFundsActor.UnblockFunds)
  }

  trait Fixture {
    val gatewayProbe = new MockGateway(PeerId("broker"))
    val fundsActor, exchangeActor = new MockSupervisedActor()
    val submissionProbe, paymentProcessorProbe, bitcoinPeerProbe, walletProbe = TestProbe()
    val entry = OrderBookEntry.fromOrder(order)
    private val calculatorStub = new AmountsCalculatorStub(amounts)
    val properties = new MutableCoinffeineNetworkProperties
    val actor = system.actorOf(Props(new OrderActor[Euro.type](
      order,
      calculatorStub,
      (publisher, funds) => new OrderController(calculatorStub, network, order, properties, publisher, funds),
      new Delegates[Euro.type] {
        override def exchangeActor(exchange: ExchangeToStart[Euro.type], resultListener: ActorRef) =
          Fixture.this.exchangeActor.props
        override def orderFundsActor: Props = fundsActor.props
      },
      OrderActor.Collaborators(walletProbe.ref, paymentProcessorProbe.ref,
        submissionProbe.ref, gatewayProbe.ref, bitcoinPeerProbe.ref)
    )))

    def givenInitializedOrder(): Unit = {
      eventually { properties.orders.get(order.id) shouldBe 'defined }
      fundsActor.expectCreation()
      fundsActor.expectMsgType[OrderFundsActor.BlockFunds]
    }

    def givenFundsBecomeAvailable(): Unit = {
      val funds = Exchange.BlockedFunds(Some(BlockedFundsId(1)), BlockedCoinsId(1))
      fundsActor.probe.send(actor, OrderFundsActor.AvailableFunds(funds))
    }

    def givenFundsBecomeUnavailable(): Unit = {
      fundsActor.probe.send(actor, OrderFundsActor.UnavailableFunds)
    }

    def givenOfflineOrder(): Unit = {
      givenInitializedOrder()
      givenFundsBecomeAvailable()
      expectProperty { _.status shouldBe OfflineOrder }
      submissionProbe.expectMsg(KeepSubmitting(entry))
    }

    def givenStalledOrder(): Unit = {
      givenOfflineOrder()
      givenFundsBecomeUnavailable()
      submissionProbe.expectMsgType[StopSubmitting]
      expectProperty { _.status should beStalled }
    }

    def givenInMarketOrder(): Unit = {
      givenOfflineOrder()
      submissionProbe.send(actor, InMarket(entry))
      expectProperty { _.status shouldBe InMarketOrder }
    }

    def givenAFreshKeyIsGenerated(): KeyPair = {
      val keyPair = new KeyPair()
      walletProbe.expectMsg(WalletActor.CreateKeyPair)
      walletProbe.reply(WalletActor.KeyPairCreated(keyPair))
      keyPair
    }

    def givenPaymentProcessorAccountIsRetrieved(): Unit = {
      paymentProcessorProbe.expectMsg(PaymentProcessorActor.RetrieveAccountId)
      val paymentProcessorId = participants.buyer.paymentProcessorAccount
      paymentProcessorProbe.reply(PaymentProcessorActor.RetrievedAccountId(paymentProcessorId))
    }

    def expectAPerfectMatchExchangeToBeStarted(): Unit = {
      givenAFreshKeyIsGenerated()
      givenPaymentProcessorAccountIsRetrieved()
      exchangeActor.expectCreation()
    }

    def givenASuccessfulPerfectMatchExchange(): Unit = {
      gatewayProbe.relayMessageFromBroker(orderMatch)
      expectAPerfectMatchExchangeToBeStarted()
      exchangeActor.probe.send(actor, ExchangeActor.ExchangeSuccess(completedExchange))
    }

    def shouldRejectAnOrderMatch(errorMessage: String): Unit = {
      val otherExchangeId = ExchangeId.random()
      gatewayProbe.relayMessageFromBroker(orderMatch.copy(exchangeId = otherExchangeId))
      gatewayProbe.expectForwardingToBroker(
        ExchangeRejection(otherExchangeId, errorMessage))
    }

    def expectProperty(f: AnyCurrencyOrder => Unit): Unit = {
      eventually {
        f(properties.orders(order.id))
      }
    }
  }

  val beStalled = new Matcher[OrderStatus] {
    override def apply(left: OrderStatus) = MatchResult(
      left.isInstanceOf[StalledOrder],
      s"$left is not stalled",
      s"$left is stalled"
    )
  }

  val beCancelled = new Matcher[OrderStatus] {
    override def apply(left: OrderStatus) = MatchResult(
      left.isInstanceOf[CancelledOrder],
      s"$left is not cancelled",
      s"$left is cancelled"
    )
  }
}
