package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.testkit._

import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.StepSignatures

class BuyerMicroPaymentChannelActorFailureTest extends BuyerMicroPaymentChannelActorTest {

  "The buyer exchange actor" should "ignore invalid signatures" in {
    val invalidDeposits = signatures.copy(buyer = MockExchangeProtocol.InvalidSignature)
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, invalidDeposits))
    listener.expectNoMsg(100.millis.dilated)
  }

  it should "fail if payments fail" in {
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 1)

    val request = paymentProcessor.expectMsgType[PaymentProcessorActor.Pay[_ <: FiatCurrency]]
    val cause = new Exception("test error")
    paymentProcessor.reply(PaymentProcessorActor.PaymentFailed(request, cause))
    listener.expectMsgPF() {
      case ChannelFailure(1, error) =>
        error.getCause shouldBe cause
    }
  }
}
