package coinffeine.peer.exchange.micropayment

import scala.language.postfixOps

import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.ChannelSuccess
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.IntermediateStep
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.protocol.messages.exchange._

class HappyPathSellerMicroPaymentChannelActorTest extends SellerMicroPaymentChannelActorTest {

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
      val signatures = StepSignatures(exchange.id, i + 1, FakeExchangeProtocol.DummySignatures)
      gateway.expectForwarding(signatures, counterpartId)
    }
  }

  it should "send the final signature" in {
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", steps), counterpartId)
    expectPaymentLookup(IntermediateStep(steps, exchange.amounts.breakdown))
    expectProgress(signatures = steps)
    val signatures = StepSignatures(
      exchange.id, exchange.amounts.breakdown.totalSteps, FakeExchangeProtocol.DummySignatures
    )
    gateway.expectForwarding(signatures, counterpartId)
  }

  it should "send a notification to the listeners once the exchange has finished" in {
    gateway.relayMessage(MicropaymentChannelClosed(exchangeId), counterpartId)
    listener.expectMsg(ChannelSuccess(None))
  }
}
