package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._

import akka.testkit.TestProbe
import org.joda.time.DateTime

import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.{Both, ExchangeId}
import coinffeine.model.network.PeerId
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor._
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.gateway.MessageGateway.Subscribe
import coinffeine.protocol.messages.brokerage.{Market, PeerPositions}
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

class BuyerMicroPaymentChannelActorTest
  extends CoinffeineClientTest("buyerExchange") with BuyerPerspective with ProgressExpectations {

  val listener = TestProbe()
  val paymentProcessor = TestProbe()
  val protocolConstants = ProtocolConstants()
  val exchangeProtocol = new MockExchangeProtocol
  val actor = system.actorOf(
    BuyerMicroPaymentChannelActor.props(exchangeProtocol, protocolConstants),
    "buyer-exchange-actor"
  )
  val signatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)
  val expectedLastOffer = {
    val initialChannel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val lastChannel = Seq.iterate(
      initialChannel, exchange.amounts.breakdown.totalSteps)(_.nextStep).last
    lastChannel.closingTransaction(signatures)
  }
  listener.watch(actor)

  "The buyer exchange actor" should "subscribe to the relevant messages when initialized" in {
    gateway.expectNoMsg()
    actor ! StartMicroPaymentChannel(
      runningExchange, paymentProcessor.ref, registryActor, Set(listener.ref)
    )

    val subscription = gateway.expectMsgType[Subscribe]
    val otherId = ExchangeId("other-id")
    val relevantOfferAccepted = StepSignatures(exchange.id, 5, signatures)
    val irrelevantOfferAccepted = StepSignatures(otherId, 2, signatures)
    val anotherPeer = PeerId("some-random-peer")
    subscription should subscribeTo(relevantOfferAccepted, counterpartId)
    subscription should not(subscribeTo(relevantOfferAccepted, anotherPeer))
    subscription should not(subscribeTo(irrelevantOfferAccepted, counterpartId))
    val randomMessage = PeerPositions.empty(Market(Euro))
    subscription should not(subscribeTo(randomMessage, counterpartId))
  }

  it should "respond to step signature messages by sending a payment until all steps are done" in {
    for (i <- 1 to exchange.amounts.breakdown.intermediateSteps) {
      actor ! fromCounterpart(StepSignatures(exchange.id, i, signatures))
      listener.expectMsgType[LastBroadcastableOffer]
      expectProgress(signatures = i, payments = i - 1)
      paymentProcessor.expectMsgType[PaymentProcessorActor.Pay[_]]
      paymentProcessor.reply(PaymentProcessorActor.Paid(
        Payment(s"payment$i", "sender", "receiver", 1.EUR, DateTime.now(), "description",
          completed = true)
      ))
      expectProgress(signatures = i, payments = i)
      shouldForward(PaymentProof(exchange.id, s"payment$i")) to counterpartId
      gateway.expectNoMsg(100 milliseconds)
    }
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    actor ! fromCounterpart(
      StepSignatures(exchange.id, exchange.amounts.breakdown.totalSteps, signatures))
    listener.expectMsgType[LastBroadcastableOffer]
    listener.expectMsg(ExchangeSuccess(Some(expectedLastOffer)))
  }
}
