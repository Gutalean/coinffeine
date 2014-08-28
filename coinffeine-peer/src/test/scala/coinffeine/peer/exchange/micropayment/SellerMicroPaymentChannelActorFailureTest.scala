package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit.TestProbe
import com.google.bitcoin.crypto.TransactionSignature
import org.scalatest.Ignore

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

}
