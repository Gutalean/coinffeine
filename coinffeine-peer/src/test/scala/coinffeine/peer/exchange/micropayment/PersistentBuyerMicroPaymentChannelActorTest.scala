package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.testkit._
import org.joda.time.DateTime

import coinffeine.model.currency._
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

class PersistentBuyerMicroPaymentChannelActorTest extends BuyerMicroPaymentChannelActorTest {

  override def protocolConstants =
    ProtocolConstants(microPaymentChannelResubmitTimeout = 2.seconds.dilated)

  "A persistent buyer exchange actor" should "persist the valid signatures received" in {
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    payerActor.expectCreation()
    payerActor.expectMsgType[PayerActor.EnsurePayment[_]]
    expectProgress(signatures = 1)
  }

  it should "remember the signatures after a restart" in {
    restartActor(expectPayerStop = true)
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 1)
  }

  it should "persist payments performed" in {
    payerActor.expectCreation()
    payerActor.expectAskWithReply {
      case PayerActor.EnsurePayment(_, pp) if pp == paymentProcessor.ref =>
        PayerActor.PaymentEnsured(paymentConfirmation("payment1"))
    }
    payerActor.stop()
    payerActor.expectStop()
    gateway.expectForwarding(PaymentProof(exchange.id, s"payment1", 1), counterpartId)
  }

  it should "remember payments performed after a restart" in {
    restartActor()
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 1)
    gateway.expectForwarding(PaymentProof(exchange.id, s"payment1", 1), counterpartId)
  }

  it should "complete the rest of intermediate steps after a restart" in {
    for (i <- 2 to lastStep) withClue(s"At step $i:") {
      actor ! fromCounterpart(StepSignatures(exchange.id, i, signatures))
      listener.expectMsgType[LastBroadcastableOffer]
      expectProgress(signatures = i)
      payerActor.expectCreation()
      payerActor.expectAskWithReply {
        case PayerActor.EnsurePayment(_, pp) if pp == paymentProcessor.ref =>
          PayerActor.PaymentEnsured(paymentConfirmation(s"payment$i"))
      }
      payerActor.stop()
      payerActor.expectStop()
      gateway.expectForwarding(PaymentProof(exchange.id, s"payment$i", i), counterpartId)
    }
  }

  it should "persist how the exchange has finished" in {
    actor ! fromCounterpart(
      StepSignatures(exchange.id, exchange.amounts.breakdown.totalSteps, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 10)
    listener.expectMsg(ChannelSuccess(Some(expectedLastOffer)))
  }

  it should "remember how the exchange terminated" in {
    restartActor()
    listener.expectMsgType[LastBroadcastableOffer]
    expectProgress(signatures = 10)
    listener.expectMsg(ChannelSuccess(Some(expectedLastOffer)))
  }

  it should "remove its journal after finish request" in {
    actor ! MicroPaymentChannelActor.Finish
    listener.expectNoMsg(100.millis.dilated)

    restartActor()
    actor ! fromCounterpart(StepSignatures(exchange.id, 1, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    payerActor.expectCreation()
    payerActor.expectMsgType[PayerActor.EnsurePayment[_]]
    expectProgress(signatures = 1)
  }

  def paymentConfirmation(paymentId: String) = PaymentProcessorActor.Paid(Payment(
    paymentId, "sender", "receiver", 1.EUR, DateTime.now(),
    "description", "invoice", completed = true
  ))
}
