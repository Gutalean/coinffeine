package coinffeine.peer.exchange.micropayment

import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.protocol.messages.exchange.StepSignatures

class BuyerMicroPaymentChannelActorFailureTest extends BuyerMicroPaymentChannelActorTest {
  "The buyer exchange actor" should "fail if the seller provides signatures" in {
    val invalidDeposits = signatures.copy(buyer = MockExchangeProtocol.InvalidSignature)
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, invalidDeposits))
    val failure = listener.expectMsgClass(classOf[ChannelFailure])
    failure.cause shouldBe an [InvalidStepSignatures]
    failure.cause.asInstanceOf[InvalidStepSignatures].step should be (1)
    listener.expectTerminated(actor)
  }
}
