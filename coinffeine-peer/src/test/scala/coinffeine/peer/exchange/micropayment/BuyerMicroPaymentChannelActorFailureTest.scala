package coinffeine.peer.exchange.micropayment

import akka.testkit.TestProbe

import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.protocol.messages.exchange.StepSignatures

class BuyerMicroPaymentChannelActorFailureTest
  extends CoinffeineClientTest("buyerExchange") with BuyerPerspective {

  val exchangeProtocol = new MockExchangeProtocol
  val signatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)

  trait Fixture {
    val listener = TestProbe()
    val paymentProcessor = TestProbe()
    val actor = system.actorOf(
      BuyerMicroPaymentChannelActor.props(
        exchangeProtocol.createMicroPaymentChannel(runningExchange),
        ProtocolConstants.Default,
        MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref))
      ),
      "buyer-exchange-actor"
    )
    listener.watch(actor)

  }

  "The buyer exchange actor" should "fail if the seller provides signatures" in new Fixture {
    val invalidDeposits = signatures.copy(buyer = MockExchangeProtocol.InvalidSignature)
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, invalidDeposits))
    val failure = listener.expectMsgClass(classOf[ChannelFailure])
    failure.cause shouldBe an [InvalidStepSignatures]
    failure.cause.asInstanceOf[InvalidStepSignatures].step should be (1)
    listener.expectTerminated(actor)
  }
}
