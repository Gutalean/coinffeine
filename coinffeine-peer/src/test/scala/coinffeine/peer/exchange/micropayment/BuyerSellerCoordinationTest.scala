package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.model.currency.Implicits._
import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.{ExchangeSuccess, LastBroadcastableOffer, StartMicroPaymentChannel}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.payment.MockPaymentProcessorFactory
import coinffeine.protocol.gateway.LinkedMessageGateways

class BuyerSellerCoordinationTest extends CoinffeineClientTest("buyerExchange") {
  val buyerListener = TestProbe()
  val sellerListener = TestProbe()
  val protocolConstants = ProtocolConstants()
  val paymentProcFactory = new MockPaymentProcessorFactory()
  val exchangeProtocol = new MockExchangeProtocol()

  val buyerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.buyer.paymentProcessorAccount, Seq(1000.EUR)))
  val buyer = system.actorOf(
    BuyerMicroPaymentChannelActor.props(exchangeProtocol, protocolConstants),
    "buyer-exchange-actor"
  )
  val buyerRunningExchange =
    buyerHandshakingExchange.startExchanging(MockExchangeProtocol.DummyDeposits)

  val sellerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.seller.paymentProcessorAccount, Seq(0.EUR)))
  val seller = system.actorOf(
    SellerMicroPaymentChannelActor.props(exchangeProtocol, protocolConstants),
    "seller-exchange-actor"
  )
  val sellerRunningExchange =
    sellerHandshakingExchange.startExchanging(MockExchangeProtocol.DummyDeposits)

  val gateways = new LinkedMessageGateways(PeerId("broker"), peerIds.buyer, peerIds.seller)

  "The buyer and seller actors" should "be able to perform an exchange" in {
    buyer ! StartMicroPaymentChannel(buyerRunningExchange, buyerPaymentProc,
      gateways.leftGateway, Set(buyerListener.ref))
    seller ! StartMicroPaymentChannel(sellerRunningExchange, sellerPaymentProc,
      gateways.rightGateway, Set(sellerListener.ref))
    buyerListener.receiveWhile() {
      case LastBroadcastableOffer(_) =>
      case ExchangeProgress(_) =>
    }
    sellerListener.receiveWhile() {
      case ExchangeProgress(_) =>
    }
    buyerListener.expectMsgType[ExchangeSuccess]
    sellerListener.expectMsg(ExchangeSuccess(None))
  }
}
