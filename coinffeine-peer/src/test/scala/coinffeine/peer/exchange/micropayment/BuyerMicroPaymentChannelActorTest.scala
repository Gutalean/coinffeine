package coinffeine.peer.exchange.micropayment

import akka.actor.ActorRef
import akka.testkit.TestProbe

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.Both
import coinffeine.model.bitcoin.TransactionSignature
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.BuyerPerspective

abstract class BuyerMicroPaymentChannelActorTest extends CoinffeineClientTest("buyerExchange")
  with BuyerPerspective with ProgressExpectations {

  def protocolConstants = ProtocolConstants.Default
  val listener, paymentProcessor = TestProbe()
  val exchangeProtocol = new FakeExchangeProtocol
  val signatures = Both(TransactionSignature.dummy, TransactionSignature.dummy)
  val lastStep = exchange.amounts.breakdown.intermediateSteps
  val expectedLastOffer = {
    val initialChannel = exchangeProtocol.createMicroPaymentChannel(runningExchange)
    val lastChannel = Seq.iterate(
      initialChannel, exchange.amounts.breakdown.totalSteps)(_.nextStep).last
    lastChannel.closingTransaction(signatures)
  }
  val payerActor = new MockSupervisedActor()
  private val props = BuyerMicroPaymentChannelActor.props(
    exchangeProtocol.createMicroPaymentChannel(runningExchange),
    protocolConstants,
    MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref)),
    new BuyerMicroPaymentChannelActor.Delegates {
      override def payer() = payerActor.props()
    }
  )
  var actor: ActorRef = _

  startActor()

  def startActor(): Unit = {
    actor = system.actorOf(props)
    listener.watch(actor)
  }

  def restartActor(expectPayerStop: Boolean = false): Unit = {
    system.stop(actor)
    if (expectPayerStop) { payerActor.expectStop() }
    listener.expectTerminated(actor)
    startActor()
  }
}
