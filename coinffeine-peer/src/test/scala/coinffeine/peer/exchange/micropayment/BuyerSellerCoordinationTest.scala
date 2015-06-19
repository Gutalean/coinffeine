package coinffeine.peer.exchange.micropayment

import akka.actor.{Actor, Props}
import akka.pattern._
import akka.testkit.TestProbe

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.{ChannelSuccess, LastBroadcastableOffer}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.payment.{MockPaymentProcessorFactory, PaymentProcessorActor}
import coinffeine.protocol.gateway.LinkedMessageGateways

class BuyerSellerCoordinationTest extends CoinffeineClientTest("buyerExchange") {
  val buyerListener, sellerListener = TestProbe()
  val protocolConstants = ProtocolConstants()
  val paymentProcFactory = new MockPaymentProcessorFactory()
  val exchangeProtocol = new FakeExchangeProtocol()
  val gateways = new LinkedMessageGateways(PeerId.hashOf("broker"), peerIds.buyer, peerIds.seller)

  val buyerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.buyer.paymentProcessorAccount, Seq(1000.EUR)))
  val buyerRunningExchange = buyerHandshakingExchange.startExchanging(
    FakeExchangeProtocol.DummyDeposits, ExchangeTimestamps.handshakingStart)
  val buyerProps = BuyerMicroPaymentChannelActor.props(
    exchangeProtocol.createMicroPaymentChannel(buyerRunningExchange),
    protocolConstants,
    MicroPaymentChannelActor.Collaborators(
      gateways.leftGateway, buyerPaymentProc, Set(buyerListener.ref)),
    new BuyerMicroPaymentChannelActor.Delegates {
      override def payer() = Props(new Actor {
        override def receive = {
          case PayerActor.EnsurePayment(req, pp) if pp == buyerPaymentProc =>
            implicit val executionContext = context.dispatcher
            AskPattern(pp, req)
              .withImmediateReply[PaymentProcessorActor.Paid[_ <: FiatCurrency]]
              .map(paid => PayerActor.PaymentEnsured(paid))
              .pipeTo(sender())
        }
      })
    }
  )

  val sellerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.seller.paymentProcessorAccount, Seq(0.EUR)))
  val sellerRunningExchange = sellerHandshakingExchange.startExchanging(
    FakeExchangeProtocol.DummyDeposits, ExchangeTimestamps.handshakingStart)
  val sellerProps = SellerMicroPaymentChannelActor.props(
    exchangeProtocol.createMicroPaymentChannel(sellerRunningExchange),
    protocolConstants,
    MicroPaymentChannelActor.Collaborators(
      gateways.rightGateway, sellerPaymentProc, Set(sellerListener.ref))
  )

  "The buyer and seller actors" should "be able to perform an exchange" in {
    system.actorOf(buyerProps, "buyer-exchange-actor")
    system.actorOf(sellerProps, "seller-exchange-actor")
    buyerListener.receiveWhile() {
      case LastBroadcastableOffer(_) =>
      case ExchangeUpdate(_) =>
    }
    sellerListener.receiveWhile() {
      case ExchangeUpdate(_) =>
    }
    buyerListener.expectMsgType[ChannelSuccess]
    sellerListener.expectMsg(ChannelSuccess(None))
  }
}
