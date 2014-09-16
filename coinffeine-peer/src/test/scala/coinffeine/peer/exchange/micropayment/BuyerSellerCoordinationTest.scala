package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.model.currency.Implicits._
import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.{ChannelSuccess, LastBroadcastableOffer}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.payment.MockPaymentProcessorFactory
import coinffeine.protocol.gateway.LinkedMessageGateways

class BuyerSellerCoordinationTest extends CoinffeineClientTest("buyerExchange") {
  val buyerListener, sellerListener = TestProbe()
  val protocolConstants = ProtocolConstants()
  val paymentProcFactory = new MockPaymentProcessorFactory()
  val exchangeProtocol = new MockExchangeProtocol()
  val gateways = new LinkedMessageGateways(PeerId("broker"), peerIds.buyer, peerIds.seller)

  val buyerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.buyer.paymentProcessorAccount, Seq(1000.EUR)))
  val buyerRunningExchange =
    buyerHandshakingExchange.startExchanging(MockExchangeProtocol.DummyDeposits)
  val buyerProps = BuyerMicroPaymentChannelActor.props(
    exchangeProtocol.createMicroPaymentChannel(buyerRunningExchange),
    protocolConstants,
    MicroPaymentChannelActor.Collaborators(
      gateways.leftGateway, buyerPaymentProc, Set(buyerListener.ref))
  )

  val sellerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.seller.paymentProcessorAccount, Seq(0.EUR)))
  val sellerRunningExchange =
    sellerHandshakingExchange.startExchanging(MockExchangeProtocol.DummyDeposits)
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
