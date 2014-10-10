package coinffeine.peer.payment.okpay

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import org.mockito.BDDMockito.given
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}
import org.scalatest.mock.MockitoSugar

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.{OkPayPaymentProcessor, Payment}
import coinffeine.peer.payment.okpay.OkPayProcessorActor.ClientFactory
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry
import coinffeine.peer.payment.okpay.ws.OkPayWebServiceClient
import coinffeine.peer.payment.{MutablePaymentProcessorProperties, PaymentProcessorActor}

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with MockitoSugar with Eventually {

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
    given(client.accountId).willReturn(senderAccount)
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.RetrieveAccountId)
    requester.expectMsg(PaymentProcessorActor.RetrievedAccountId(senderAccount))
  }

  it must "be able to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    requester.send(processor, PaymentProcessorActor.RetrieveBalance(UsDollar))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.RetrieveTotalBlockedFunds(UsDollar) =>
        BlockedFiatRegistry.TotalBlockedFunds(13.USD)
    }
    requester.expectMsg(PaymentProcessorActor.BalanceRetrieved(amount, 13.USD))
  }

  it must "update properties when asked to get the current balance" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    val nextAmount = amount * 2
    given(client.currentBalances()).willReturn(Future.successful(Seq(nextAmount)))
    requester.send(processor, PaymentProcessorActor.RetrieveBalance(UsDollar))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.RetrieveTotalBlockedFunds(UsDollar) =>
        BlockedFiatRegistry.TotalBlockedFunds(13.USD)
    }
    requester.expectMsgType[PaymentProcessorActor.BalanceRetrieved[FiatCurrency]]

    expectBalanceUpdate(nextAmount)
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    given(client.currentBalances()).willReturn(Future.failed(cause))
    given(client.currentBalance(UsDollar)).willReturn(Future.failed(cause))
    processor = system.actorOf(processorProps)
    requester.send(processor, ServiceActor.Start({}))
    requester.expectMsg(ServiceActor.Started)
    requester.send(processor, PaymentProcessorActor.RetrieveBalance(UsDollar))
    requester.expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(UsDollar, cause))
  }

  it must "delegate funds blocking and unblocking" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized()
    val blockRequest = PaymentProcessorActor.BlockFunds(funds, amount)
    requester.send(processor, blockRequest)
    fundsRegistry.expectForward(blockRequest, requester.ref)
    val unblockRequest = PaymentProcessorActor.UnblockFunds(funds)
    requester.send(processor, unblockRequest)
    fundsRegistry.expectForward(unblockRequest, requester.ref)
  }

  it must "be able to send a payment that gets reserved funds reduced" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment"))
      .willReturn(Future.successful(payment))
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenPaymentProcessorIsInitialized(balances = Seq(amountPlusFee))
    requester.send(processor, PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment"))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(`funds`, `amountPlusFee`) =>
        BlockedFiatRegistry.FundsUsed(funds, amountPlusFee)
    }
    val response = requester.expectMsgType[PaymentProcessorActor.Paid[_ <: FiatCurrency]].payment
    response.id should be (payment.id)
    response.senderId should be (senderAccount)
    response.receiverId should be (receiverAccount)
    response.amount should be (amount)
    response.description should be ("comment")

    withClue("the fee has been taken into account") {
      requester.send(
        processor, PaymentProcessorActor.Pay(funds, receiverAccount, 0.01.USD, "comment"))
      requester.expectMsgPF() {
        case PaymentProcessorActor.PaymentFailed(_, ex) =>
          ex.toString should include ("fail to use funds")
      }
    }
  }

  it must "require enough funds to send a payment" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    requester.send(processor, PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment"))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(_, requested) =>
        BlockedFiatRegistry.CannotUseFunds(funds, requested, "not enough!")
    }
    requester.expectMsgClass(classOf[PaymentProcessorActor.PaymentFailed[_]])
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    given(client.sendPayment(receiverAccount, amount, "comment")).willReturn(Future.failed(cause))
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenPaymentProcessorIsInitialized(balances = Seq(amountPlusFee))
    val payRequest = PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment")
    requester.send(processor, payRequest)
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(`funds`, `amountPlusFee`) =>
        BlockedFiatRegistry.FundsUsed(funds, amountPlusFee)
    }
    requester.expectMsg(PaymentProcessorActor.PaymentFailed(payRequest, cause))
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(Some(payment)))
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsgType[PaymentProcessorActor.PaymentFound]
  }

  it must "be able to check a payment does not exist"  in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.successful(None))
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsg(PaymentProcessorActor.PaymentNotFound(payment.id))
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    given(client.findPayment(payment.id)).willReturn(Future.failed(cause))
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsg(PaymentProcessorActor.FindPaymentFailed(payment.id, cause))
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    given(client.currentBalances()).willReturn(
      Future.successful(Seq(120.EUR)),
      Future.successful(Seq(140.EUR)),
      Future.failed(new Exception("doesn't work"))
    )
    expectBalanceUpdate(120.EUR, timeout = 2.seconds)
    expectBalanceUpdate(140.EUR, timeout = 2.seconds)
    expectBalanceUpdate(140.EUR, hasExpired = true, timeout = 2.seconds)
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
    val funds = ExchangeId.random()
    val cause = new Exception("Sample error")
    val client = mock[OkPayClient]
    var processor: ActorRef = _
    val properties = new MutablePaymentProcessorProperties
    private val clientFactory = new ClientFactory {
      override def build(): OkPayClient = client
      override def shutdown(): Unit = {}
    }
    val fundsRegistry = new MockSupervisedActor()
    val processorProps = Props(new OkPayProcessorActor(
      clientFactory, fundsRegistry.props, pollingInterval, properties))
    val eventsProbe, requester = TestProbe()
    system.eventStream.subscribe(
      eventsProbe.ref, classOf[PaymentProcessorActor.FundsAvailabilityEvent])

    def givenPaymentProcessorIsInitialized(balances: Seq[FiatAmount] = Seq.empty): Unit = {
      given(client.currentBalances()).willReturn(Future.successful(balances))
      processor = system.actorOf(processorProps)
      fundsRegistry.expectCreation()
      requester.send(processor, ServiceActor.Start({}))
      requester.expectMsg(ServiceActor.Started)
      fundsRegistry.expectMsgType[BlockedFiatRegistry.BalancesUpdate]
    }

    def expectBalanceUpdate(balance: FiatAmount,
                            hasExpired: Boolean = false,
                            timeout: FiniteDuration = 200.millis): Unit = {
      eventually(PatienceConfiguration.Timeout(timeout)) {
        properties.balance(balance.currency) should be (FiatBalance(balance, hasExpired))
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
