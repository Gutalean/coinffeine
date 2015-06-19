package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.testkit._
import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually

import coinffeine.model.currency.Euro
import coinffeine.model.payment.Payment
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.IntermediateStep
import coinffeine.peer.exchange.protocol.{FakeExchangeProtocol, MockMicroPaymentChannel}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.peer.payment.PaymentProcessorActor.{FindPaymentCriterion, FindPayment, PaymentFound}
import coinffeine.protocol.messages.exchange._

abstract class SellerMicroPaymentChannelActorTest extends CoinffeineClientTest("sellerExchange")
  with SellerPerspective with ProgressExpectations[Euro.type] with Eventually {

  val listener, paymentProcessor = TestProbe()
  val protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1.second.dilated,
    refundSignatureAbortTimeout = 1.minute,
    microPaymentChannelResubmitTimeout = 2.seconds.dilated
  )
  private val props = SellerMicroPaymentChannelActor.props(
    new MockMicroPaymentChannel(runningExchange),
    protocolConstants,
    MicroPaymentChannelActor.Collaborators(gateway.ref, paymentProcessor.ref, Set(listener.ref))
  )
  var actor: ActorRef = _
  startActor()

  val firstStep = IntermediateStep(1, exchange.amounts.breakdown)
  val steps = exchange.amounts.breakdown.intermediateSteps
  val firstSignatures = StepSignatures(exchange.id, 1, FakeExchangeProtocol.DummySignatures)
  val secondSignatures = StepSignatures(exchange.id, 2, FakeExchangeProtocol.DummySignatures)

  protected def startActor(): Unit = {
    actor = system.actorOf(props)
    listener.watch(actor)
  }

  protected def restartActor(): Unit = {
    system.stop(actor)
    listener.expectTerminated(actor)
    startActor()
  }

  protected def expectPaymentLookup(step: IntermediateStep, completed: Boolean = true): Unit = {
    val FindPayment(FindPaymentCriterion.ById(paymentId)) = paymentProcessor.expectMsgType[FindPayment]
    paymentProcessor.reply(PaymentFound(Payment(
      id = paymentId,
      senderId = participants.buyer.paymentProcessorAccount,
      receiverId = participants.seller.paymentProcessorAccount,
      description = PaymentFields.description(exchange.id, step),
      invoice = PaymentFields.invoice(exchange.id, step),
      amount = step.select(exchange.amounts).fiatAmount,
      date = DateTime.now(),
      completed = completed
    )))
  }
}
