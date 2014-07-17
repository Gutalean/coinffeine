package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Props
import org.joda.time.DateTime
import org.mockito.BDDMockito.given
import org.mockito.Matchers.any
import org.scalatest.mock.MockitoSugar

import coinffeine.common.test.AkkaSpec
import coinffeine.model.currency.Currency.UsDollar
import coinffeine.model.currency.Implicits._
import coinffeine.model.payment.Payment
import coinffeine.peer.payment.PaymentProcessor

class OKPayProcessorActorTest extends AkkaSpec("OkPayTest") with MockitoSugar {

  val futureTimeout = 5.seconds
  val senderAccount = "OK12345"
  val receiverAccount = "OK54321"
  val token = "token"
  val amount = 100.USD
  val payment = Payment(
    id = "250092",
    senderId = senderAccount,
    receiverId = receiverAccount,
    amount = amount,
    date = OkPayWebServiceClient.DateFormat.parseDateTime("2014-01-20 14:00:00"),
    description = "comment"
  )

  private trait WithOkPayProcessor {
    val client = mock[OkPayClient]
    val fakeTokenGenerator = mock[TokenGenerator]
    given(fakeTokenGenerator.build(any[DateTime])).willReturn(token)
    val processor = system.actorOf(Props(new OKPayProcessorActor(senderAccount, client)))
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

  it must "be able to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    processor ! PaymentProcessor.Pay(receiverAccount, amount, "comment")
    expectMsgPF() {
      case PaymentProcessor.Paid(Payment(
        "250092", `senderAccount`, `receiverAccount`, `amount`, _, "comment")) =>
    }
  }

  it must "be able to retrieve a existing payment" in new WithOkPayProcessor {
    given(client.findPayment("250092")).willReturn(Future.successful(Some(payment)))
    processor ! PaymentProcessor.FindPayment("250092")
    expectMsgPF() {
      case PaymentProcessor.PaymentFound(Payment(
      "250092", `senderAccount`, `receiverAccount`, `amount`, _, "comment")) =>
    }
  }
}
