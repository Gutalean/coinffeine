package coinffeine.peer.payment.okpay

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe
import org.mockito.BDDMockito.given
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Currency.UsDollar
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{FiatAmount, FiatCurrency}
import coinffeine.model.payment.{Payment, PaymentProcessor}
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.payment.PaymentProcessorActor

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with MockitoSugar {

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized()
    processor ! PaymentProcessorActor.RetrieveAccountId
    expectMsg(PaymentProcessorActor.RetrievedAccountId(senderAccount))
  }

  it must "be able to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrieved(amount, UsDollar.Zero))
  }

  it must "produce an event when asked to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    given(client.currentBalances()).willReturn(Future.successful(Seq(amount * 2)))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsgClass(classOf[PaymentProcessorActor.BalanceRetrieved[FiatCurrency]])
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(amount * 2))
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalances()).willReturn(Future.failed(cause))
    given(client.currentBalance(UsDollar)).willReturn(Future.failed(cause))
    processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(UsDollar, cause))
  }

  it must "be able to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.BlockFunds(amount)
    val funds = expectMsgClass(classOf[PaymentProcessor.BlockedFundsId])
    expectMsg(PaymentProcessorActor.AvailableFunds(funds))
    processor ! PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment")
    expectMsgPF() {
      case PaymentProcessorActor.Paid(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment", _)) =>
    }
  }

  it must "require enough funds to send a payment" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.BlockFunds(amount / 2)
    val funds = expectMsgClass(classOf[PaymentProcessor.BlockedFundsId])
    expectMsg(PaymentProcessorActor.AvailableFunds(funds))
    processor ! PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment")
    expectMsgClass(classOf[PaymentProcessorActor.PaymentFailed[_]])
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment")).willReturn(Future.failed(cause))
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.BlockFunds(amount)
    val funds = expectMsgClass(classOf[PaymentProcessor.BlockedFundsId])
    expectMsg(PaymentProcessorActor.AvailableFunds(funds))
    val payRequest = PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment")
    processor ! payRequest
    expectMsg(PaymentProcessorActor.PaymentFailed(payRequest, cause))
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(Some(payment)))
    givenPaymentProcessorIsInitialized()
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsgPF() {
      case PaymentProcessorActor.PaymentFound(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment", _)) =>
    }
  }

  it must "be able to check a payment does not exist"  in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(None))
    givenPaymentProcessorIsInitialized()
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsg(PaymentProcessorActor.PaymentNotFound(payment.id))
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.failed(cause))
    givenPaymentProcessorIsInitialized()
    processor ! PaymentProcessorActor.FindPayment(payment.id)
    expectMsg(PaymentProcessorActor.FindPaymentFailed(payment.id, cause))
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    given(client.currentBalances()).willReturn(
      Future.successful(Seq(120.EUR)),
      Future.successful(Seq(140.EUR))
    )
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(120.EUR))
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(140.EUR))
  }

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

    def givenPaymentProcessorIsInitialized(balances: Seq[FiatAmount] = Seq.empty): Unit = {
      given(client.currentBalances()).willReturn(Future.successful(balances))
      processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
      for (i <- 1 to balances.size) {
        eventChannelProbe.expectMsgClass(classOf[FiatBalanceChangeEvent])
      }
    }
  }

  class MutableBalances(initialBalances: FiatAmount*) extends Answer[Future[Seq[FiatAmount]]] {
    private val balances = new AtomicReference[Seq[FiatAmount]](initialBalances)

    override def answer(invocation: InvocationOnMock) = Future.successful(balances.get())

    def set(newBalances: FiatAmount*): Unit = {
      balances.set(newBalances)
    }
  }
}
