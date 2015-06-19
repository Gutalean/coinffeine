package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.StepSignatures

class BuyerMicroPaymentChannelActorFailureTest extends BuyerMicroPaymentChannelActorTest {

  "The buyer exchange actor" should "ignore invalid signatures" in {
    val invalidDeposits = signatures.copy(buyer = FakeExchangeProtocol.InvalidSignature)
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, invalidDeposits))
    listener.expectNoMsg(100.millis.dilated)
  }

  it should "fail if payments fail" in {
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 1)

    val cause = new Exception("test error")
    payerActor.expectCreation()
    payerActor.expectAskWithReply {
      case PayerActor.EnsurePayment(req, pp) if pp == paymentProcessor.ref =>
        PayerActor.CannotEnsurePayment(req, cause)
    }
    listener.expectMsgPF() {
      case ChannelFailure(1, error) => error shouldBe cause
    }
  }

  it should "remember how it failed" in {
    restartActor()
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 1)
    listener.expectMsgType[ChannelFailure]
  }
}
