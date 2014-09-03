package coinffeine.peer.exchange.micropayment

import scala.language.postfixOps

import akka.testkit.TestProbe
import com.google.bitcoin.crypto.TransactionSignature

import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective

class SellerMicroPaymentChannelActorFailureTest
  extends CoinffeineClientTest("buyerExchange") with SellerPerspective {

  val dummySig = TransactionSignature.dummy

  trait Fixture {
    val listener = TestProbe()
    val paymentProcessor = TestProbe()
    val actor = system.actorOf(
      SellerMicroPaymentChannelActor.props(new MockExchangeProtocol(), ProtocolConstants.Default))
    listener.watch(actor)
  }
}
