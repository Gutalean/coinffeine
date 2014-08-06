package coinffeine.peer.market

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.api.event.{OrderStatusChangedEvent, OrderSubmittedEvent}
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.exchange.ExchangeActor
import coinffeine.peer.market.SubmissionSupervisor.{InMarket, KeepSubmitting, StopSubmitting}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.FundsId
import coinffeine.protocol.gateway.GatewayProbe
import coinffeine.protocol.messages.brokerage.OrderMatch

class OrderActorTest extends AkkaSpec {

  "A bidding order actor" should "keep order info" in new BiddingFixture {
    actor ! OrderActor.RetrieveStatus
    expectMsg(stalledOrder)
  }

  it should "block FIAT funds when is initialized" in new BiddingFixture {
    givenInitializedOrder()
  }

  it should "keep in stalled status when there are not enough funds when buying" in new BiddingFixture {
    givenInitializedOrder()
    paymentProcessorProbe.reply(PaymentProcessorActor.NotEnoughFunds)
    eventChannelProbe.expectNoMsg()
    submissionProbe.expectNoMsg()
  }

  it should "move to stalled when payment processor reports unavailable funds" in new BiddingFixture {
    givenOfflineOrder()
    paymentProcessorProbe.send(actor, PaymentProcessorActor.UnavailableFunds(fundsId))
    eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, OfflineOrder, StalledOrder))
    submissionProbe.expectMsg(StopSubmitting(order.id))
  }

  it should "move to offline when receive available funds" in new BiddingFixture {
    givenStalledOrder()
    paymentProcessorProbe.send(actor, PaymentProcessorActor.AvailableFunds(FundsId(1)))
    eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, StalledOrder, OfflineOrder))
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
    actor ! OrderActor.CancelOrder
    submissionProbe.expectMsg(StopSubmitting(order.id))
    eventChannelProbe.expectMsgPF() {
      case OrderStatusChangedEvent(orderId, _, CancelledOrder(_)) if orderId == order.id =>
    }
  }

  it should "move from stalled to offline when available funds message is received" in new BiddingFixture {
    givenStalledOrder()
    paymentProcessorProbe.send(actor, PaymentProcessorActor.AvailableFunds(fundsId))
    eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, StalledOrder, OfflineOrder))
    submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
  }

  it should "stop submitting to the broker & send event once matching is received" in new BiddingFixture {
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
        order.amount, order.price * order.amount.value, Exchange.StepBreakdown(10))
    )))
    eventChannelProbe.fishForMessage() {
      case OrderStatusChangedEvent(orderId, _, CompletedOrder) if orderId == order.id => true
      case _ => false
    }
  }

  it should "spawn an exchange upon matching" in new BiddingFixture {
    givenInMarketOrder()
    gatewayProbe.relayMessage(orderMatch, brokerId)
    val keyPair = new KeyPair()

    walletProbe.expectMsg(WalletActor.CreateKeyPair)
    walletProbe.reply(WalletActor.KeyPairCreated(keyPair))

    paymentProcessorProbe.expectMsg(PaymentProcessorActor.Identify)
    paymentProcessorProbe.reply(PaymentProcessorActor.Identified(paymentProcessorId))

    exchange.expectCreation()
    val peerInfo = Exchange.PeerInfo(paymentProcessorId, keyPair)
    exchange.expectMsgPF {
      case ExchangeActor.StartExchange(ex, `role`, `peerInfo`, _, _, _, _)
        if ex.id == exchangeId =>
    }
  }

  "An asking order actor" should "keep order info" in new AskingFixture {
    actor ! OrderActor.RetrieveStatus
    expectMsg(offlineOrder)
  }

  it should "not block FIAT funds when is initialized" in new AskingFixture {
    givenOfflineOrder()
    paymentProcessorProbe.expectNoMsg()
  }

  it should "submit to the broker and receive submission status" in new AskingFixture {
    givenInMarketOrder()
  }

  it should "keep submitting to the broker until been cancelled" in new AskingFixture {
    givenInMarketOrder()
    actor ! OrderActor.CancelOrder
    submissionProbe.expectMsg(StopSubmitting(order.id))
    eventChannelProbe.expectMsgPF() {
      case OrderStatusChangedEvent(order.id, InMarketOrder, CancelledOrder(_)) =>
    }
  }

  it should "stop submitting to the broker & send event once matching is received" in new AskingFixture {
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
        order.amount, order.price * order.amount.value, Exchange.StepBreakdown(10))
    )))
    eventChannelProbe.fishForMessage() {
      case OrderStatusChangedEvent(orderId, _, CompletedOrder) if orderId == order.id => true
      case _ => false
    }
  }

  it should "spawn an exchange upon matching" in new AskingFixture {
    givenInMarketOrder()
    gatewayProbe.relayMessage(orderMatch, brokerId)
    val keyPair = new KeyPair()

    walletProbe.expectMsg(WalletActor.CreateKeyPair)
    walletProbe.reply(WalletActor.KeyPairCreated(keyPair))

    paymentProcessorProbe.expectMsg(PaymentProcessorActor.Identify)
    paymentProcessorProbe.reply(PaymentProcessorActor.Identified(paymentProcessorId))

    exchange.expectCreation()
    val peerInfo = Exchange.PeerInfo(paymentProcessorId, keyPair)
    exchange.expectMsgPF {
      case ExchangeActor.StartExchange(ex, role, `peerInfo`, _, _, _, _)
        if ex.id == exchangeId =>
    }
  }

  trait Fixture extends CoinffeineUnitTestNetwork.Component {
    val gatewayProbe = new GatewayProbe()
    val submissionProbe, eventChannelProbe, paymentProcessorProbe, bitcoinPeerProbe, walletProbe =
      TestProbe()
    val fundsId = FundsId(1)
    val brokerId = PeerId("broker")
    val exchange = new MockSupervisedActor()
    val actor = system.actorOf(Props(new OrderActor(exchange.props, network, 10)))
    val order: Order[FiatCurrency]
    val role: Role
    val paymentProcessorId = "account-123"
    val stalledOrder = order.withStatus(StalledOrder)
    val offlineOrder = order.withStatus(OfflineOrder)
    val inMarketOrder = order.withStatus(InMarketOrder)

    val exchangeId = ExchangeId.random()
    val counterpart = PeerId("counterpart")
    val orderMatch = OrderMatch(
      order.id, exchangeId, order.amount, order.price, lockTime = 400000L, counterpart)

    actor ! OrderActor.Initialize(order, submissionProbe.ref, eventChannelProbe.ref,
      gatewayProbe.ref, paymentProcessorProbe.ref, bitcoinPeerProbe.ref, walletProbe.ref, brokerId)
    gatewayProbe.expectSubscription()
  }

  trait BiddingFixture extends Fixture {
    override lazy val order: Order[FiatCurrency] = Order(Bid, 5.BTC, 500.EUR)
    override val role: Role = BuyerRole

    def givenInitializedOrder(): Unit = {
      eventChannelProbe.expectMsg(OrderSubmittedEvent(stalledOrder))
      paymentProcessorProbe.expectMsg(PaymentProcessorActor.BlockFunds(order.fiatAmount, actor))
    }


    def givenOfflineOrder(): Unit = {
      givenInitializedOrder()
      paymentProcessorProbe.reply(PaymentProcessorActor.FundsBlocked(fundsId))
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, StalledOrder, OfflineOrder))
      submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
    }

    def givenStalledOrder(): Unit = {
      givenOfflineOrder()
      paymentProcessorProbe.reply(PaymentProcessorActor.UnavailableFunds(fundsId))
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, OfflineOrder, StalledOrder))
      submissionProbe.expectMsg(StopSubmitting(order.id))
    }

    def givenInMarketOrder(): Unit = {
      givenOfflineOrder()
      submissionProbe.send(actor, InMarket(OrderBookEntry(order)))
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, OfflineOrder, InMarketOrder))
    }
  }

  trait AskingFixture extends Fixture {
    override lazy val order: Order[FiatCurrency] = Order(Ask, 5.BTC, 500.EUR)
    override val role: Role = SellerRole

    def givenOfflineOrder(): Unit = {
      eventChannelProbe.expectMsg(OrderSubmittedEvent(offlineOrder))
      submissionProbe.expectMsg(KeepSubmitting(OrderBookEntry(order)))
    }

    def givenInMarketOrder(): Unit = {
      givenOfflineOrder()
      submissionProbe.send(actor, InMarket(OrderBookEntry(order)))
      eventChannelProbe.expectMsg(OrderStatusChangedEvent(order.id, OfflineOrder, InMarketOrder))
    }
  }
}
