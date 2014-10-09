package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit._
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually

import coinffeine.model.currency.Euro
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
    resubmitHandshakeMessagesTimeout = 1.second.dilated,
    refundSignatureAbortTimeout = 1.minute,
    microPaymentChannelResubmitTimeout = 2.seconds.dilated
  )
  val channel = new MockMicroPaymentChannel(runningExchange)
  val firstStep = IntermediateStep(1, exchange.amounts.breakdown)
  val actor = system.actorOf(
    SellerMicroPaymentChannelActor.props(channel, protocolConstants,
      MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref))),
    "seller-exchange-actor"
  )
  listener.watch(actor)

  val steps = exchange.amounts.breakdown.intermediateSteps
  val firstSignatures = StepSignatures(exchange.id, 1, MockExchangeProtocol.DummySignatures)
  val secondSignatures = StepSignatures(exchange.id, 2, MockExchangeProtocol.DummySignatures)

  "The seller exchange actor" should "send the first step signature as soon as the exchange starts" in {
    expectProgress(signatures = 1)
    gateway.expectForwarding(firstSignatures, counterpartId)
  }

  it should "not send the second step signature until complete payment proof has been provided" in {
    gateway.relayMessage(PaymentProof(exchange.id, "INCOMPLETE", 1), counterpartId)
    expectPaymentLookup(firstStep, completed = false)
    withClue("instead, send previous signatures") {
      gateway.expectForwarding(firstSignatures, counterpartId)
    }
  }

  it should "ignore payment proofs that don't apply to the current step" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 3), counterpartId)
    withClue("keep sending previous signatures") {
      gateway.expectForwarding(firstSignatures, counterpartId)
    }
  }

  it should "send the second step signature once payment proof has been provided" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 1), counterpartId)
    expectPaymentLookup(firstStep)
    expectProgress(signatures = 1)
    gateway.expectForwarding(secondSignatures, counterpartId)
    expectProgress(signatures = 2)
  }

  it should "keep sending them if no response is received" in {
    gateway.expectForwarding(secondSignatures, counterpartId)
  }

  it should "send step signatures as new payment proofs are provided" in {
    for (i <- 2 to (steps - 1)) {
      val step = IntermediateStep(i, exchange.amounts.breakdown)
      gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", i), counterpartId)
      expectPaymentLookup(step)
      expectProgress(signatures = i + 1)
      val signatures = StepSignatures(exchange.id, i + 1, MockExchangeProtocol.DummySignatures)
      gateway.expectForwarding(signatures, counterpartId)
    }
  }

  it should "send the final signature" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", steps), counterpartId)
    expectPaymentLookup(IntermediateStep(steps, exchange.amounts.breakdown))
    expectProgress(signatures = steps)
    val signatures = StepSignatures(
      exchange.id, exchange.amounts.breakdown.totalSteps, MockExchangeProtocol.DummySignatures
    )
    gateway.expectForwarding(signatures, counterpartId)
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    gateway.relayMessage(MicropaymentChannelClosed(exchangeId), counterpartId)
    listener.expectMsg(ChannelSuccess(None))
  }

  private def expectPaymentLookup(step: IntermediateStep, completed: Boolean = true): Unit = {
    println(s"step $step")
    val FindPayment(paymentId) = paymentProcessor.expectMsgType[FindPayment]
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
