package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe
import org.mockito.BDDMockito.given
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.AkkaSpec
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.payment.Payment
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.payment.PaymentProcessor

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with MockitoSugar {

  private trait WithOkPayProcessor {
    def pollingInterval = 3.seconds
    val senderAccount = "OK12345"
    val receiverAccount = "OK54321"
    val amount = 100.USD
    val payment = Payment(
      id = "250092",
      senderId = senderAccount,
      receiverId = receiverAccount,
      amount = amount,
      date = OkPayWebServiceClient.DateFormat.parseDateTime("2014-01-20 14:00:00"),
      description = "comment"
    )
    val cause = new Exception("Sample error")
    val client = mock[OkPayClient]
    val eventChannelProbe = TestProbe()
    val processor = system.actorOf(Props(
      new OkPayProcessorActor(senderAccount, client, pollingInterval)))
    processor ! PaymentProcessor.Initialize(eventChannelProbe.ref)
  }

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
    processor ! PaymentProcessor.Identify
    expectMsg(PaymentProcessor.Identified("OKPAY"))
  }

  it must "be able to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.successful(amount))
    processor ! PaymentProcessor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessor.BalanceRetrieved(amount))
  }

  it must "produce an event when asked to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.successful(amount))
    processor ! PaymentProcessor.RetrieveBalance(UsDollar)
    expectMsgClass(classOf[PaymentProcessor.BalanceRetrieved[FiatCurrency]])
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(amount))
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.failed(cause))
    processor ! PaymentProcessor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessor.BalanceRetrievalFailed(UsDollar, cause))
  }

  it must "be able to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    processor ! PaymentProcessor.Pay(receiverAccount, amount, "comment")
    expectMsgPF() {
      case PaymentProcessor.Paid(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment")) =>
    }
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment")).willReturn(Future.failed(cause))
    val payRequest = PaymentProcessor.Pay(receiverAccount, amount, "comment")
    processor ! payRequest
    expectMsg(PaymentProcessor.PaymentFailed(payRequest, cause))
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(Some(payment)))
    processor ! PaymentProcessor.FindPayment(payment.id)
    expectMsgPF() {
      case PaymentProcessor.PaymentFound(Payment(
      payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment")) =>
    }
  }

  it must "be able to check a payment does not exist"  in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(None))
    processor ! PaymentProcessor.FindPayment(payment.id)
    expectMsg(PaymentProcessor.PaymentNotFound(payment.id))
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.failed(cause))
    processor ! PaymentProcessor.FindPayment(payment.id)
    expectMsg(PaymentProcessor.FindPaymentFailed(payment.id, cause))
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    given(client.currentBalance(Euro)).willReturn(Future.successful(100.EUR))
    override def pollingInterval = 1.second
    eventChannelProbe.expectNoMsg(500.millis)
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
    eventChannelProbe.expectNoMsg(500.millis)
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
  }
}
