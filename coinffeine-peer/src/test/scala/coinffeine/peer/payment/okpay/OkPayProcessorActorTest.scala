package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.Props
import akka.testkit.TestProbe
import org.mockito.BDDMockito.given
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.currency.Currency.UsDollar
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{FiatAmount, FiatCurrency}
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

    def givenPaymentProcessorIsInitialized(balances: Seq[FiatAmount] = Seq.empty): Unit = {
      given(client.currentBalances()).willReturn(Future.successful(balances))
      processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
    }
  }

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized()
    processor ! PaymentProcessorActor.Identify
    expectMsg(PaymentProcessorActor.Identified("OKPAY"))
  }

  it must "be able to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrieved(amount))
  }

  it must "produce an event when asked to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsgClass(classOf[PaymentProcessorActor.BalanceRetrieved[FiatCurrency]])
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(amount))
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalances()).willReturn(Future.failed(cause))
    processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
    processor ! PaymentProcessorActor.RetrieveBalance(UsDollar)
    expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(UsDollar, cause))
  }

  it must "be able to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    processor ! PaymentProcessorActor.Pay(receiverAccount, amount, "comment")
    expectMsgPF() {
      case PaymentProcessorActor.Paid(Payment(
        payment.id, `senderAccount`, `receiverAccount`, `amount`, _, "comment", _)) =>
    }
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment")).willReturn(Future.failed(cause))
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    val payRequest = PaymentProcessorActor.Pay(receiverAccount, amount, "comment")
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
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    override def pollingInterval = 1.second
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
  }

  it must "block funds up to existing balances" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
    processor ! PaymentProcessorActor.BlockFunds(50.EUR, self)
    processor ! PaymentProcessorActor.BlockFunds(50.EUR, self)
    processor ! PaymentProcessorActor.BlockFunds(50.EUR, self)
    expectMsgAllConformingOf(
      classOf[PaymentProcessorActor.FundsBlocked],
      classOf[PaymentProcessorActor.FundsBlocked],
      PaymentProcessorActor.NotEnoughFunds.getClass
    )
  }

  it must "unblock blocked funds to make then available again" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    eventChannelProbe.expectMsg(FiatBalanceChangeEvent(100.EUR))
    processor ! PaymentProcessorActor.BlockFunds(100.EUR, self)
    val funds = expectMsgClass(classOf[PaymentProcessorActor.FundsBlocked]).funds
    processor ! PaymentProcessorActor.UnblockFunds(funds)
    processor ! PaymentProcessorActor.BlockFunds(100.EUR, self)
    expectMsgClass(classOf[PaymentProcessorActor.FundsBlocked])
  }

  it must "notify when blocked funds are not backed by funds" in new WithOkPayProcessor {
    override def pollingInterval = 100.millis
    given(client.currentBalances())
      .willReturn(Future.successful(Seq(100.EUR)), Future.successful(Seq(90.EUR)))
    processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
    val listener1, listener2 = TestProbe()
    processor ! PaymentProcessorActor.BlockFunds(90.EUR, listener1.ref)
    processor ! PaymentProcessorActor.BlockFunds(10.EUR, listener2.ref)
    listener1.expectMsgClass(classOf[PaymentProcessorActor.UnavailableFunds])
    listener2.expectMsgClass(classOf[PaymentProcessorActor.UnavailableFunds])
  }

  it must "notify when blocked funds are available again due to balance increase" in
    new WithOkPayProcessor {
      override def pollingInterval = 100.millis
      given(client.currentBalances()).willReturn(
        Future.successful(Seq(100.EUR)),
        Future.successful(Seq(50.EUR)),
        Future.successful(Seq(100.EUR))
      )
      processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
      val listener = TestProbe()
      processor ! PaymentProcessorActor.BlockFunds(100.EUR, listener.ref)
      listener.expectMsgClass(classOf[PaymentProcessorActor.UnavailableFunds])
      listener.expectMsgClass(classOf[PaymentProcessorActor.AvailableFunds])
    }

  it must "notify when blocked funds are available again due to fund unblocking" in
    new WithOkPayProcessor {
      override def pollingInterval = 100.millis
      given(client.currentBalances()).willReturn(
        Future.successful(Seq(100.EUR)),
        Future.successful(Seq(50.EUR))
      )
      processor ! PaymentProcessorActor.Initialize(eventChannelProbe.ref)
      val listener1, listener2 = TestProbe()
      processor ! PaymentProcessorActor.BlockFunds(50.EUR, listener1.ref)
      processor ! PaymentProcessorActor.BlockFunds(50.EUR, listener2.ref)
      val PaymentProcessorActor.FundsBlocked(fundsId) = fishForMessage() {
        case PaymentProcessorActor.FundsBlocked(_) => true
      }
      listener1.expectMsgClass(classOf[PaymentProcessorActor.UnavailableFunds])
      listener2.expectMsgClass(classOf[PaymentProcessorActor.UnavailableFunds])
      processor ! PaymentProcessorActor.UnblockFunds(fundsId)
      listener2.expectMsgClass(classOf[PaymentProcessorActor.AvailableFunds])
    }
}
