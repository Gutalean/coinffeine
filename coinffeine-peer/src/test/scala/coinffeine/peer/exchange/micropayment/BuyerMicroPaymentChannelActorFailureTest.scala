package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe

import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.ExchangeActor.ExchangeProgress
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.protocol.messages.exchange.StepSignatures

class BuyerMicroPaymentChannelActorFailureTest
  extends CoinffeineClientTest("buyerExchange") with BuyerPerspective {

  val protocolConstants = ProtocolConstants(exchangeSignatureTimeout = 0.5 seconds)
  val exchangeProtocol = new MockExchangeProtocol
  val signatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)

  trait Fixture {
    val listener = TestProbe()
    val paymentProcessor = TestProbe()
    val actor = system.actorOf(Props(new BuyerMicroPaymentChannelActor(new MockExchangeProtocol)))
    listener.watch(actor)

    def startMicroPaymentChannel(): Unit = {
      actor ! StartMicroPaymentChannel(runningExchange, protocolConstants, paymentProcessor.ref,
        gateway.ref, Set(listener.ref))
    }
  }

  "The buyer exchange actor" should "return a failure message if the seller does not provide the" +
    " step signature within the specified timeout" in new Fixture {
    startMicroPaymentChannel()
    val failure = listener.expectMsgClass(classOf[ExchangeFailure])
    failure.cause shouldBe a [TimeoutException]
    actor ! GetLastOffer
    expectMsg(LastOffer(None))
  }

  it should "return the last signed offer when a timeout happens" in new Fixture {
    startMicroPaymentChannel()
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, signatures))
    listener.expectMsgClass(classOf[ExchangeProgress])
    val failure = listener.expectMsgClass(classOf[ExchangeFailure])
    failure.cause shouldBe a [TimeoutException]
    actor ! GetLastOffer
    val lastOfferReply = expectMsgClass(classOf[LastOffer])
    lastOfferReply.lastOffer should be ('defined)
  }

  it should "return a failure message if the seller provides an invalid signature" in new Fixture {
    startMicroPaymentChannel()
    val invalidDeposits = signatures.copy(buyer = MockExchangeProtocol.InvalidSignature)
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, invalidDeposits))
    val failure = listener.expectMsgClass(classOf[ExchangeFailure])
    failure.cause shouldBe an [InvalidStepSignatures]
    failure.cause.asInstanceOf[InvalidStepSignatures].step should be (1)
    actor ! GetLastOffer
    expectMsg(LastOffer(None))
  }
}
