package coinffeine.peer.market

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.{BlockedCoinsId, KeyPair}
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.{FiatAmount, BitcoinAmount, FiatCurrency}
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.api.event.{OrderProgressedEvent, OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.OrderActor.{NoFundsMessage, BlockingFundsMessage}
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.GatewayProbe
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActorTest extends AkkaSpec {

  "A bidding order actor" should "keep order info" in new BiddingFixture {
    actor ! OrderActor.RetrieveStatus
    expectMsg(blockingFundsOrder)
  }

  it should "block FIAT funds when initialized" in new BiddingFixture {
    givenInitializedOrder()
  }

  it should "keep in stalled status when there are not enough funds when buying" in
    new BiddingFixture {
      givenInitializedOrder()
      givenFundsBecomeUnavailable()
      eventChannelProbe.expectNoMsg()
      submissionProbe.expectNoMsg()
    }

  it should "move to stalled when payment processor reports unavailable funds" in
    new BiddingFixture {
      givenOfflineOrder()
      givenFundsBecomeUnavailable()
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(
        order.id, OfflineOrder, StalledOrder(NoFundsMessage)))
      submissionProbe.expectMsg(StopSubmitting(order.id))
    }

  it should "move to offline when receive available funds" in new BiddingFixture {
    givenStalledOrder()
    givenFundsBecomeAvailable()
    eventChannelProbe.expectMsg(OrderStatusChangedEvent(
      order.id, StalledOrder(NoFundsMessage), OfflineOrder))
    submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
  }

  it should "submit to the broker and receive submission status" in new BiddingFixture {
    givenOfflineOrder()
    val entry = OrderBookEntry(order)
    submissionProbe.send(actor, InMarket(entry))
    eventChannelProbe.expectMsgPF() {
      case OrderStatusChangedEvent(orderId, _, InMarketOrder) if orderId == order.id =>
    }
  }

  it should "keep submitting to the broker until been cancelled" in new BiddingFixture {
    givenOfflineOrder()
    expectNoMsg()
    val reason = "some reason"
    actor ! OrderActor.CancelOrder(reason)
    submissionProbe.expectMsg(StopSubmitting(order.id))
    eventChannelProbe.expectMsgPF() {
      case OrderStatusChangedEvent(orderId, _, CancelledOrder(`reason`)) if orderId == order.id =>
    }
  }

  it should "release funds when being cancelled" in new BiddingFixture {
    givenOfflineOrder()
    actor ! OrderActor.CancelOrder("testing purposes")
    fundsActor.expectMsg(OrderFundsActor.UnblockFunds)
  }

  it should "move from stalled to offline when available funds message is received" in
    new BiddingFixture {
      givenStalledOrder()
      givenFundsBecomeAvailable()
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(
        order.id, StalledOrder(NoFundsMessage), OfflineOrder))
      submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
    }

  it should "stop submitting to the broker & report new status once matching is received" in
    new BiddingFixture {
      givenInMarketOrder()
      gatewayProbe.relayMessage(orderMatch, brokerId)
      submissionProbe.fishForMessage() {
        case StopSubmitting(orderId) if orderId == order.id => true
        case _ => false
      }
      eventChannelProbe.expectMsgAllOf(
        OrderStatusChangedEvent(order.id, InMarketOrder, InProgressOrder),
        OrderProgressedEvent(order.id, 0.0, 0.0)
      )
      exchange.probe.send(actor, ExchangeActor.ExchangeSuccess(completedExchange))
      eventChannelProbe.fishForMessage() {
        case OrderStatusChangedEvent(orderId, _, CompletedOrder) if orderId == order.id => true
        case _ => false
      }
    }

  it should "spawn an exchange upon matching" in new BiddingFixture {
    givenInMarketOrder()
    gatewayProbe.relayMessage(orderMatch, brokerId)
    val keyPair = givenAFreshKeyIsGenerated()
    givenPaymentProcessorAccountIsRetrieved()

    exchange.expectCreation()
    val peerInfo = Exchange.PeerInfo(paymentProcessorId, keyPair)
    exchange.expectMsgPF {
      case ExchangeActor.StartExchange(ex, `role`, `peerInfo`, _, _, _, _)
        if ex.id == exchangeId =>
    }
  }

  it should "release remaining funds after completing exchanges" in new BiddingFixture {
    givenInMarketOrder()
    givenASuccessfulPerfectMatchExchange()
    fundsActor.expectMsg(OrderFundsActor.UnblockFunds)
  }

  "An asking order actor" should "keep order info" in new AskingFixture {
    actor ! OrderActor.RetrieveStatus
    expectMsg(blockingFundsOrder)
  }

  it should "submit to the broker and receive submission status" in new AskingFixture {
    givenInMarketOrder()
  }

  it should "keep submitting to the broker until been cancelled" in new AskingFixture {
    givenInMarketOrder()
    val reason = "some reason"
    actor ! OrderActor.CancelOrder(reason)
    submissionProbe.expectMsg(StopSubmitting(order.id))
    eventChannelProbe.expectMsgPF() {
      case OrderStatusChangedEvent(order.id, InMarketOrder, CancelledOrder(`reason`)) =>
    }
  }

  it should "stop submitting to the broker & send event once matching is received" in
    new AskingFixture {
      givenInMarketOrder()
      gatewayProbe.relayMessage(orderMatch, brokerId)
      submissionProbe.fishForMessage() {
        case StopSubmitting(orderId) if orderId == order.id => true
        case _ => false
      }
      exchange.probe.send(actor, ExchangeActor.ExchangeSuccess(CompletedExchange(
        id = exchangeId,
        role,
        counterpart,
        parameters = Exchange.Parameters(10, network),
        brokerId,
        amounts = Exchange.Amounts[FiatCurrency](
          order.amount, order.price * order.amount.value, Exchange.StepBreakdown(10)),
        blockedFunds
      )))
      eventChannelProbe.fishForMessage() {
        case OrderStatusChangedEvent(orderId, _, CompletedOrder) => orderId == order.id
        case _ => false
      }
    }

  it should "spawn an exchange upon matching" in new AskingFixture {
    givenInMarketOrder()
    gatewayProbe.relayMessage(orderMatch, brokerId)
    expectAPerfectMatchExchangeToBeStarted()
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val role: Role
    def fiatFunds: Option[BlockedFundsId]
    val order: Order[FiatCurrency]
    def amountsToBlock: (FiatAmount, BitcoinAmount)
    val gatewayProbe = new GatewayProbe()
    val fundsActor = new MockSupervisedActor()
    val submissionProbe, eventChannelProbe, paymentProcessorProbe, bitcoinPeerProbe, walletProbe =
      TestProbe()
    def blockedFunds = Exchange.BlockedFunds(fiatFunds, BlockedCoinsId(1))
    val brokerId = PeerId("broker")
    val exchange = new MockSupervisedActor()
    val actor = system.actorOf(Props(new OrderActor(exchange.props, fundsActor.props, network, 10)))
    val paymentProcessorId = "account-123"
    val blockingFundsOrder = order.withStatus(StalledOrder(BlockingFundsMessage))
    val offlineOrder = order.withStatus(OfflineOrder)
    val inMarketOrder = order.withStatus(InMarketOrder)

    val exchangeId = ExchangeId.random()
    val counterpart = PeerId("counterpart")
    val orderMatch = OrderMatch(
      order.id, exchangeId, order.amount, order.price, lockTime = 400000L, counterpart)
    val completedExchange = CompletedExchange(
      id = exchangeId,
      role,
      counterpart,
      parameters = Exchange.Parameters(10, network),
      brokerId,
      amounts = Exchange.Amounts[FiatCurrency](
        order.amount, order.price * order.amount.value, Exchange.StepBreakdown(10)),
      blockedFunds
    )

    actor ! OrderActor.Initialize(order, submissionProbe.ref, eventChannelProbe.ref,
      gatewayProbe.ref, paymentProcessorProbe.ref, bitcoinPeerProbe.ref, walletProbe.ref, brokerId)
    gatewayProbe.expectSubscription()
    fundsActor.expectCreation()

    def givenInitializedOrder(): Unit = {
      eventChannelProbe.expectMsg(OrderSubmittedEvent(blockingFundsOrder))
      fundsActor.expectMsg(OrderFundsActor.BlockFunds(
        amountsToBlock._1, amountsToBlock._2, walletProbe.ref, paymentProcessorProbe.ref))
    }

    def givenOfflineOrder(): Unit = {
      givenInitializedOrder()
      givenFundsBecomeAvailable()
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(
        order.id, StalledOrder(BlockingFundsMessage), OfflineOrder))
      submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
    }

    def givenFundsBecomeAvailable(): Unit = {
      fundsActor.probe.send(actor, OrderFundsActor.AvailableFunds(blockedFunds))
    }

    def givenFundsBecomeUnavailable(): Unit = {
      fundsActor.probe.send(actor, OrderFundsActor.UnavailableFunds)
    }

    def givenInMarketOrder(): Unit = {
      givenOfflineOrder()
      submissionProbe.send(actor, InMarket(OrderBookEntry(order)))
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, OfflineOrder, InMarketOrder))
    }

    def givenAFreshKeyIsGenerated(): KeyPair = {
      val keyPair = new KeyPair()
      walletProbe.expectMsg(WalletActor.CreateKeyPair)
      walletProbe.reply(WalletActor.KeyPairCreated(keyPair))
      keyPair
    }

    def givenPaymentProcessorAccountIsRetrieved(): Unit = {
      paymentProcessorProbe.expectMsg(PaymentProcessorActor.RetrieveAccountId)
      paymentProcessorProbe.reply(PaymentProcessorActor.RetrievedAccountId(paymentProcessorId))
    }

    def expectAPerfectMatchExchangeToBeStarted(): Unit = {
      val keyPair = givenAFreshKeyIsGenerated()
      givenPaymentProcessorAccountIsRetrieved()

      exchange.expectCreation()
      val peerInfo = Exchange.PeerInfo(paymentProcessorId, keyPair)
      exchange.expectMsgPF {
        case ExchangeActor.StartExchange(ex, `role`, `peerInfo`, _, _, _, _)
          if ex.id == exchangeId =>
      }
    }

    def givenASuccessfulPerfectMatchExchange(): Unit = {
      gatewayProbe.relayMessage(orderMatch, brokerId)
      expectAPerfectMatchExchangeToBeStarted()
      exchange.probe.send(actor, ExchangeActor.ExchangeSuccess(completedExchange))
    }
  }

  trait BiddingFixture extends Fixture {
    override lazy val order: Order[FiatCurrency] = Order(Bid, 5.BTC, 500.EUR)
    override val role: Role = BuyerRole
    override val fiatFunds = Some(BlockedFundsId(1))
    override val amountsToBlock = (order.fiatAmount, order.amount * 0.2)

    def givenStalledOrder(): Unit = {
      givenOfflineOrder()
      givenFundsBecomeUnavailable()
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(
        order.id, OfflineOrder, StalledOrder(NoFundsMessage)))
      submissionProbe.expectMsg(StopSubmitting(order.id))
    }
  }

  trait AskingFixture extends Fixture {
    override lazy val order: Order[FiatCurrency] = Order(Ask, 5.BTC, 500.EUR)
    override val role: Role = SellerRole
    override val fiatFunds = None
    override val amountsToBlock = (0.EUR, order.amount * 1.1)
  }
}
