package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe
import org.mockito.BDDMockito.given
import org.scalatest.mock.MockitoSugar
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Currency.{Euro, UsDollar}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.payment.Payment
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.payment.PaymentProcessorActor

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
      description = "comment",
      completed = true
    )
    val cause = new Exception("Sample error")
    val client = mock[OkPayClient]
    val eventChannelProbe = TestProbe()
    val processor = system.actorOf(Props(
      new OkPayProcessorActor(senderAccount, client, pollingInterval)))
    processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
  }

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
    processor ! PaymentProcessorActor.Identify
    expectMsg(PaymentProcessorActor.Identified("OKPAY"))
  }

  it must "be able to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.successful(amount))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrieved(amount))
  }

  it must "produce an event when asked to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.successful(amount))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsgClass(classOf[PaymentProcessorActor.BalanceRetrieved[FiatCurrency]])
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(amount))
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalance(UsDollar)).willReturn(Future.failed(cause))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(UsDollar, cause))
  }

  it must "be able to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    processor ! PaymentProcessorActor.Pay(receiverAccount, amount, "comment")
    expectMsgPF() {
      case PaymentProcessorActor.Paid(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment", _)) =>
    }
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment")).willReturn(Future.failed(cause))
    val payRequest = PaymentProcessorActor.Pay(receiverAccount, amount, "comment")
    processor ! payRequest
    expectMsg(PaymentProcessorActor.PaymentFailed(payRequest, cause))
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(Some(payment)))
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsgPF() {
      case PaymentProcessorActor.PaymentFound(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment", _)) =>
    }
  }

  it must "be able to check a payment does not exist"  in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(None))
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsg(PaymentProcessorActor.PaymentNotFound(payment.id))
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.failed(cause))
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsg(PaymentProcessorActor.FindPaymentFailed(payment.id, cause))
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    given(client.currentBalance(Euro)).willReturn(Future.successful(100.EUR))
    override def pollingInterval = 1.second
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
  }
}
