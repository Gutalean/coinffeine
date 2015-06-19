package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.testkit._

import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.ChannelSuccess
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.IntermediateStep
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.protocol.messages.exchange._

class PersistentSellerMicroPaymentChannelActorTest extends SellerMicroPaymentChannelActorTest {

  "The seller exchange actor" should "persist accepted payments" in {
    expectProgress(signatures = 1)
    gateway.expectForwarding(firstSignatures, counterpartId)
    gateway.relayMessage(PaymentProof(exchange.id, "PROOF!", 1), counterpartId)
    expectPaymentLookup(firstStep)

    expectProgress(signatures = 2)
    gateway.expectForwarding(secondSignatures, counterpartId)
  }

  it should "remember accepted payments after a restart" in {
    restartActor()
    expectProgress(signatures = 2)
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

  it should "finish by request and remove its journal" in {
    actor ! MicroPaymentChannelActor.Finish
    expectNoMsg(100.millis.dilated)

    restartActor()
    expectProgress(signatures = 1)
  }
}
