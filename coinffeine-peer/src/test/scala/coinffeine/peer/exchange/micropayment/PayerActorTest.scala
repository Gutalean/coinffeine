package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.TestPayment
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment.okpay.OkPayClient.DuplicatedPayment

class PayerActorTest extends AkkaSpec {

  "Payer actor" should "reply with payment ensured if everything goes well" in new Fixture {
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(response)
    requester.expectMsg(PayerActor.PaymentEnsured(response))
  }

  it should "retry if payment processor does not respond" in new Fixture {
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    requester.expectNoMsg(retryTimeout.duration)
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(response)
    requester.expectMsg(PayerActor.PaymentEnsured(response))
  }

  it should "retry if payment processor responds with error other than dup payment" in new Fixture {
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(PaymentProcessorActor.PaymentFailed(request, injectedError))
    requester.expectNoMsg(retryTimeout.duration / 2)
    paymentProcessor.expectMsg(request)
  }

  it should "fetch payment details if payment processor response was lost" in new Fixture {
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    requester.expectNoMsg(retryTimeout.duration)
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(dupPaymentFailed)
    paymentProcessor.expectMsg(FindPayment(FindPaymentCriterion.ByInvoice(payment.invoice)))
    paymentProcessor.reply(PaymentFound(payment))
    requester.expectMsg(PayerActor.PaymentEnsured(response))
  }

  it should "fail & stop if find payment responds with not found after dup payment" in new Fixture {
    val findPaymentCriterion = FindPaymentCriterion.ByInvoice(payment.invoice)
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    requester.expectNoMsg(retryTimeout.duration)
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(dupPaymentFailed)
    paymentProcessor.expectMsg(FindPayment(findPaymentCriterion))
    paymentProcessor.reply(PaymentNotFound(findPaymentCriterion))
    requester.expectMsgType[PayerActor.CannotEnsurePayment]
  }

  it should "retry payment retrieval by invoice if first attempt fails" in new Fixture {
    val findPaymentCriterion = FindPaymentCriterion.ByInvoice(payment.invoice)
    requester.send(payer, PayerActor.EnsurePayment(request, paymentProcessor = paymentProcessor.ref))
    paymentProcessor.expectMsg(request)
    requester.expectNoMsg(retryTimeout.duration)
    paymentProcessor.expectMsg(request)
    paymentProcessor.reply(dupPaymentFailed)
    paymentProcessor.expectMsg(FindPayment(findPaymentCriterion))
    paymentProcessor.reply(FindPaymentFailed(findPaymentCriterion, injectedError))
    paymentProcessor.expectMsg(FindPayment(findPaymentCriterion))
  }

  trait Fixture {
    val retryTimeout = 250.millis
    val requester = TestProbe()
    val paymentProcessor = TestProbe()
    val payment = TestPayment.random(completed = true)
    val request = PaymentProcessorActor.Pay(
      ExchangeId("pay-id"),
      payment.receiverId,
      payment.netAmount,
      payment.description,
      payment.invoice
    )
    val response = PaymentProcessorActor.Paid(payment)
    val dupPaymentFailed = PaymentProcessorActor.PaymentFailed(
      request, DuplicatedPayment(payment.receiverId, payment.invoice, null))
    val payer = system.actorOf(PayerActor.props(retryTimeout.dilated))
    val injectedError = new Error("injected error") with NoStackTrace
  }

}
