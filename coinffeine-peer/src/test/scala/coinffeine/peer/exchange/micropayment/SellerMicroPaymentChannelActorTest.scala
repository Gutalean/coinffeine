package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit.TestProbe
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually

import coinffeine.model.currency.Currency.Euro
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.network.PeerId
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.{ExchangeSuccess, StartMicroPaymentChannel}
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.IntermediateStep
import coinffeine.peer.exchange.protocol.{MockExchangeProtocol, MockMicroPaymentChannel}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.peer.payment.PaymentProcessorActor.{FindPayment, PaymentFound}
import coinffeine.protocol.gateway.MessageGateway.Subscribe
import coinffeine.protocol.messages.brokerage.{Market, PeerPositions}
import coinffeine.protocol.messages.exchange._

class SellerMicroPaymentChannelActorTest extends CoinffeineClientTest("sellerExchange")
  with SellerPerspective with ProgressExpectations with Eventually {

  val listener = TestProbe()
  val paymentProcessor = TestProbe()
  val protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitRefundSignatureTimeout = 1 second,
    refundSignatureAbortTimeout = 1 minute,
    microPaymentChannelResubmitTimeout = 2.seconds
  )
  val channel = new MockMicroPaymentChannel(runningExchange)
  val firstStep = IntermediateStep(1, exchange.amounts.breakdown)
  val actor = system.actorOf(
    SellerMicroPaymentChannelActor.props(new MockExchangeProtocol(), protocolConstants),
    "seller-exchange-actor"
  )
  listener.watch(actor)

  actor ! StartMicroPaymentChannel(
    runningExchange, paymentProcessor.ref, registryActor, Set(listener.ref)
  )

  "The seller exchange actor" should "subscribe to the relevant messages" in {
    val subscription = gateway.expectMsgClass(classOf[Subscribe])
    val anotherPeer = PeerId("some-random-peer")
    val relevantPayment = PaymentProof(exchange.id, null)
    val irrelevantPayment = PaymentProof(ExchangeId("another-id"), null)
    subscription should subscribeTo(relevantPayment, counterpartId)
    subscription should not(subscribeTo(relevantPayment, anotherPeer))
    subscription should not(subscribeTo(irrelevantPayment, counterpartId))
    val randomMessage = PeerPositions.empty(Market(Euro))
    subscription should not(subscribeTo(randomMessage, counterpartId))
  }

  val firstSignatures = StepSignatures(exchange.id, 1, MockExchangeProtocol.DummySignatures)
  val secondSignatures = StepSignatures(exchange.id, 2, MockExchangeProtocol.DummySignatures)

  it should "send the first step signature as soon as the exchange starts" in {
    expectProgress(signatures = 1, payments = 0)
    shouldForward(firstSignatures) to counterpartId
  }

  it should "not send the second step signature until complete payment proof has been provided" in {
    actor ! fromCounterpart(PaymentProof(exchange.id, "INCOMPLETE"))
    expectPayment(firstStep, completed = false)
    withClue("instead, send previous signatures") {
      shouldForward(firstSignatures) to counterpartId
    }
  }

  it should "send the second step signature once payment proof has been provided" in {
    actor ! fromCounterpart(PaymentProof(exchange.id, "PROOF!"))
    expectPayment(firstStep)
    expectProgress(signatures = 1, payments = 1)
    eventually {
      shouldForward(secondSignatures) to counterpartId
    }
    expectProgress(signatures = 2, payments = 1)
  }

  it should "keep sending them if no response is received" in {
    expectNoMsg(100.millis)
    shouldForward(secondSignatures) to counterpartId
  }

  it should "send step signatures as new payment proofs are provided" in {
    actor ! fromCounterpart(PaymentProof(exchange.id, "PROOF!"))
    expectPayment(IntermediateStep(2, exchange.amounts.breakdown))
    expectProgress(signatures = 2, payments = 2)
    for (i <- 3 to exchange.amounts.breakdown.intermediateSteps) {
      val step = IntermediateStep(i, exchange.amounts.breakdown)
      expectProgress(signatures = i, payments = i - 1)
      actor ! fromCounterpart(PaymentProof(exchange.id, "PROOF!"))
      expectPayment(step)
      expectProgress(signatures = i, payments = i)
      val signatures = StepSignatures(exchange.id, i, MockExchangeProtocol.DummySignatures)
      shouldForward(signatures) to counterpartId
    }
  }

  it should "send the final signature" in {
    val signatures = StepSignatures(
      exchange.id, exchange.amounts.breakdown.totalSteps, MockExchangeProtocol.DummySignatures
    )
    shouldForward(signatures) to counterpartId
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    listener.expectMsg(ExchangeSuccess(None))
  }

  private def expectPayment(step: IntermediateStep, completed: Boolean = true): Unit = {
    val FindPayment(paymentId) = paymentProcessor.expectMsgClass(classOf[FindPayment])
    paymentProcessor.reply(PaymentFound(Payment(
      id = paymentId,
      senderId = participants.buyer.paymentProcessorAccount,
      receiverId = participants.seller.paymentProcessorAccount,
      description = PaymentDescription(exchange.id, step),
      amount = exchange.amounts.stepFiatAmount,
      date = DateTime.now(),
      completed = completed
    )))
  }
}
