package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit.TestProbe
import com.google.bitcoin.crypto.TransactionSignature

import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective

class SellerMicroPaymentChannelActorFailureTest
  extends CoinffeineClientTest("buyerExchange") with SellerPerspective {

  val protocolConstants = ProtocolConstants(exchangePaymentProofTimeout = 0.5 seconds)
  val dummySig = TransactionSignature.dummy

  trait Fixture {
    val listener = TestProbe()
    val paymentProcessor = TestProbe()
    val actor = system.actorOf(
      SellerMicroPaymentChannelActor.props(new MockExchangeProtocol(), protocolConstants))
    listener.watch(actor)
  }

  "The seller exchange actor" should "return a failure message if the buyer does not provide the" +
    " necessary payment proof within the specified timeout" in new Fixture{
    actor ! StartMicroPaymentChannel(
      runningExchange, paymentProcessor.ref, gateway.ref, Set(listener.ref)
    )
    listener.expectMsgClass(classOf[ExchangeProgress])
    val failure = listener.expectMsgClass(classOf[ExchangeFailure])
    failure.cause shouldBe a [TimeoutException]
    actor ! GetLastOffer
    expectMsg(LastOffer(None))
  }
}
