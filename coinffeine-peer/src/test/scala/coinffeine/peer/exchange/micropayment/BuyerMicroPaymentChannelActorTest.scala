package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.testkit.TestProbe
import org.joda.time.DateTime

import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Both
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

class BuyerMicroPaymentChannelActorTest extends CoinffeineClientTest("buyerExchange")
  with BuyerPerspective with ProgressExpectations[Euro.type] {

  val listener = TestProbe()
  val paymentProcessor = TestProbe()
  val protocolConstants = ProtocolConstants(microPaymentChannelResubmitTimeout = 2.seconds)
  val exchangeProtocol = new MockExchangeProtocol
  val actor = system.actorOf(
    BuyerMicroPaymentChannelActor.props(
      exchangeProtocol.createMicroPaymentChannel(runningExchange),
      protocolConstants,
      MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref))
    ),
    "buyer-exchange-actor"
  )
  val signatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)
  val lastStep = exchange.amounts.breakdown.intermediateSteps
  val expectedLastOffer = {
    val initialChannel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val lastChannel = Seq.iterate(
      initialChannel, exchange.amounts.breakdown.totalSteps)(_.nextStep).last
    lastChannel.closingTransaction(signatures)
  }
  listener.watch(actor)

  "The buyer exchange actor" should "subscribe to the relevant messages when initialized" in {
    gateway.expectNoMsg()
    gateway.expectSubscription()
  }

  it should "respond to step signature messages by sending a payment until all steps are done" in {
    for (i <- 1 to lastStep) withClue(s"At step $i:") {
      actor ! fromCounterpart(StepSignatures(exchange.id, i, signatures))
      listener.expectMsgType[LastBroadcastableOffer]
      expectProgress(signatures = i, payments = i - 1)
      paymentProcessor.expectMsgType[PaymentProcessorActor.Pay[_]]
      paymentProcessor.reply(PaymentProcessorActor.Paid(
        Payment(s"payment$i", "sender", "receiver", 1.EUR, DateTime.now(), "description",
          completed = true)
      ))
      expectProgress(signatures = i, payments = i)
      gateway.expectForwarding(PaymentProof(exchange.id, s"payment$i", i), counterpartId)
      gateway.expectNoMsg(100 milliseconds)
    }
  }

  it should "resubmit payment proof when no response is get" in {
    gateway.expectForwarding(PaymentProof(exchange.id, s"payment$lastStep", lastStep),
      counterpartId)
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    actor ! fromCounterpart(
      StepSignatures(exchange.id, exchange.amounts.breakdown.totalSteps, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    listener.expectMsg(ChannelSuccess(Some(expectedLastOffer)))
  }
}
