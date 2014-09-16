package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit.TestProbe
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.ChannelSuccess
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.IntermediateStep
import coinffeine.peer.exchange.protocol.{MockExchangeProtocol, MockMicroPaymentChannel}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.peer.payment.PaymentProcessorActor.{FindPayment, PaymentFound}
import coinffeine.protocol.messages.exchange._

class SellerMicroPaymentChannelActorTest extends CoinffeineClientTest("sellerExchange")
  with SellerPerspective with ProgressExpectations[Euro.type] with Eventually {

  val listener = TestProbe()
  val paymentProcessor = TestProbe()
  val protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1 second,
    refundSignatureAbortTimeout = 1 minute,
    microPaymentChannelResubmitTimeout = 2.seconds
  )
  val channel = new MockMicroPaymentChannel(runningExchange)
  val firstStep = IntermediateStep(1, exchange.amounts.breakdown)
  val actor = system.actorOf(
    SellerMicroPaymentChannelActor.props(channel, protocolConstants,
      MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref))),
    "seller-exchange-actor"
  )
  listener.watch(actor)

  val firstSignatures = StepSignatures(exchange.id, 1, MockExchangeProtocol.DummySignatures)
  val secondSignatures = StepSignatures(exchange.id, 2, MockExchangeProtocol.DummySignatures)

  "The seller exchange actor" should "send the first step signature as soon as the exchange starts" in {
    expectProgress(signatures = 1, payments = 0)
    gateway.expectForwarding(firstSignatures, counterpartId)
  }

  it should "not send the second step signature until complete payment proof has been provided" in {
    gateway.relayMessage(PaymentProof(exchange.id, "INCOMPLETE", 1), counterpartId)
    expectPayment(firstStep, completed = false)
    withClue("instead, send previous signatures") {
      gateway.expectForwarding(firstSignatures, counterpartId)
    }
  }

  it should "ignore payment proofs that don't apply to the current step" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 3), counterpartId)
    expectNoMsg()
  }

  it should "send the second step signature once payment proof has been provided" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 1), counterpartId)
    expectPayment(firstStep)
    expectProgress(signatures = 1, payments = 1)
    eventually {
      gateway.expectForwarding(secondSignatures, counterpartId)
    }
    expectProgress(signatures = 2, payments = 1)
  }

  it should "keep sending them if no response is received" in {
    expectNoMsg(100.millis)
    gateway.expectForwarding(secondSignatures, counterpartId)
  }

  it should "send step signatures as new payment proofs are provided" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 2), counterpartId)
    expectPayment(IntermediateStep(2, exchange.amounts.breakdown))
    expectProgress(signatures = 2, payments = 2)
    for (i <- 3 to exchange.amounts.breakdown.intermediateSteps) {
      val step = IntermediateStep(i, exchange.amounts.breakdown)
      expectProgress(signatures = i, payments = i - 1)
      gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", i), counterpartId)
      expectPayment(step)
      expectProgress(signatures = i, payments = i)
      val signatures = StepSignatures(exchange.id, i, MockExchangeProtocol.DummySignatures)
      gateway.expectForwarding(signatures, counterpartId)
    }
  }

  it should "send the final signature" in {
    val signatures = StepSignatures(
      exchange.id, exchange.amounts.breakdown.totalSteps, MockExchangeProtocol.DummySignatures
    )
    gateway.expectForwarding(signatures, counterpartId)
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    gateway.relayMessage(MicropaymentChannelClosed(exchangeId), counterpartId)
    listener.expectMsg(ChannelSuccess(None))
  }

  private def expectPayment(step: IntermediateStep, completed: Boolean = true): Unit = {
    val FindPayment(paymentId) = paymentProcessor.expectMsgClass(classOf[FindPayment])
    paymentProcessor.reply(PaymentFound(Payment(
      id = paymentId,
      senderId = participants.buyer.paymentProcessorAccount,
      receiverId = participants.seller.paymentProcessorAccount,
      description = PaymentDescription(exchange.id, step),
      amount = step.select(exchange.amounts).fiatAmount,
      date = DateTime.now(),
      completed = completed
    )))
  }
}
