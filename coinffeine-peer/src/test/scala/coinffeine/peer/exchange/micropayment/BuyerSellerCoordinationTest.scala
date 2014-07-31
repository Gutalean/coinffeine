package coinffeine.peer.exchange.micropayment

import akka.actor.{Actor, ActorRef, Props}
import akka.testkit.TestProbe
import org.scalatest.mock.MockitoSugar

import coinffeine.model.currency.Implicits._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.{ExchangeSuccess, StartMicroPaymentChannel}
import coinffeine.peer.exchange.protocol._
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.payment.MockPaymentProcessorFactory
import coinffeine.protocol.gateway.MessageGateway.{ForwardMessage, ReceiveMessage}

class BuyerSellerCoordinationTest extends CoinffeineClientTest("buyerExchange") with MockitoSugar {
  val buyerListener = TestProbe()
  val sellerListener = TestProbe()
  val protocolConstants = ProtocolConstants()
  val paymentProcFactory = new MockPaymentProcessorFactory()
  val exchangeProtocol = new MockExchangeProtocol()

  class MessageForwarder(to: ActorRef) extends Actor {
    override val receive: Receive = {
      case ForwardMessage(msg, dest) => to ! ReceiveMessage(msg, dest)
    }
  }

  object MessageForwarder {
    def apply(name: String, to: ActorRef): ActorRef = system.actorOf(
      Props(new MessageForwarder(to)), name)
  }

  val buyerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.buyer.paymentProcessorAccount, Seq(1000.EUR)))
  val buyer = system.actorOf(
    Props(new BuyerMicroPaymentChannelActor(exchangeProtocol)),
    "buyer-exchange-actor"
  )

  val sellerPaymentProc = system.actorOf(paymentProcFactory.newProcessor(
    participants.seller.paymentProcessorAccount, Seq(0.EUR)))
  val seller = system.actorOf(
    Props(new SellerMicroPaymentChannelActor(exchangeProtocol)),
    "seller-exchange-actor"
  )

  "The buyer and seller actors" should "be able to perform an exchange" in {
    buyer ! StartMicroPaymentChannel(
      buyerRunningExchange, protocolConstants, buyerPaymentProc,
      MessageForwarder("fw-to-seller", seller), Set(buyerListener.ref))
    seller ! StartMicroPaymentChannel(
      sellerRunningExchange, protocolConstants, sellerPaymentProc,
      MessageForwarder("fw-to-buyer", buyer), Set(sellerListener.ref))
    buyerListener.receiveWhile() {
      case ExchangeProgress(_) =>
    }
    sellerListener.receiveWhile() {
      case ExchangeProgress(_) =>
    }
    buyerListener.expectMsgClass(classOf[ExchangeSuccess])
    sellerListener.expectMsg(ExchangeSuccess(None))
  }
}
