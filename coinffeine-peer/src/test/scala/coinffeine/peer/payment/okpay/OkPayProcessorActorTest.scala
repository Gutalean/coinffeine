package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.{OkPayPaymentProcessor, Payment}
import coinffeine.model.util.CacheStatus
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.FindPaymentCriterion
import coinffeine.peer.payment.okpay.OkPayProcessorActor.ClientFactory
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry
import coinffeine.peer.payment.okpay.ws.OkPayWebServiceClient
import coinffeine.peer.properties.fiat.DefaultPaymentProcessorProperties

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with Eventually {

  "OKPayProcessor" must "be able to get the current balance" in new WithOkPayProcessor {
    givenClientBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenBalanceIsRequested(UsDollar)
    expectRetrieveBlockedFunds(13.USD)
    expectBalanceRetrieved(amount, 13.USD)
  }

  it must "update properties when asked to get the current balance" in new WithOkPayProcessor {
    givenClientBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    givenClientBalance(amount * 2)
    whenBalanceIsRequested(UsDollar)
    expectRetrieveBlockedFunds(UsDollar)
    expectBalancePropertyUpdated(amount * 2)
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    givenBalanceRetrievalFailure()
    givenStartedPaymentProcessor()
    whenBalanceIsRequested(UsDollar)
    expectBalanceRetrievalFailure(UsDollar)
  }

  it must "delegate funds blocking" in new WithReadyOkPayProcessor {
    val blockRequest = PaymentProcessorActor.BlockFunds(funds, amount)
    whenFundsBlockingIsRequested(blockRequest)
    expectForwardedToRegistry(blockRequest)
  }

  it must "delegate funds unblocking" in new WithReadyOkPayProcessor {
    val unblockRequest = PaymentProcessorActor.UnblockFunds(funds)
    whenFundsUnblockingIsRequested(unblockRequest)
    expectForwardedToRegistry(unblockRequest)
  }

  it must "be able to send a payment that gets reserved funds reduced" in new WithOkPayProcessor {
    givenClientPaymentWillSucceedWith(payment)
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenClientBalance(amountPlusFee)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenPaymentIsRequested(amount)
    expectRegistryMarksUsed(amountPlusFee)
    expectPaymentSuccess(amount)

    withClue("amount and its fee are blocked and paid") {
      whenPaymentIsRequested(0.01.USD)
      expectPaymentFailed("fail to use funds")
    }
  }

  it must "require enough funds to send a payment" in new WithOkPayProcessor {
    givenClientBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenPaymentIsRequested(amount)
    expectRegistryCannotMarkUsed()
    expectPaymentFailed("fail to use funds")
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    givenClientPaymentWillFail()
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenClientBalance(amountPlusFee)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenPaymentIsRequested(amount)
    expectRegistryMarksUsed(amountPlusFee)
    expectRegistryMarksUnused(amountPlusFee)
    expectPaymentFailed()
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    givenClientExistingPayment(payment)
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectPaymentFound(payment)
  }

  it must "be able to check a payment does not exist" in new WithOkPayProcessor {
    givenClientNonExistingPayment(payment)
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectPaymentNotFound(payment)
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    givenClientPaymentCannotBeRetrieved(payment)
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectFindPaymentFailed(payment)
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenClientBalance(100.EUR)
    givenStartedPaymentProcessor()
    givenClientBalance(120.EUR)
    expectBalancePropertyUpdated(120.EUR, timeout = 2.seconds.dilated)
    givenClientBalance(140.EUR)
    expectBalancePropertyUpdated(140.EUR, timeout = 2.seconds.dilated)
    givenBalanceRetrievalFailure()
    expectBalancePropertyUpdated(
      140.EUR, cacheStatus = CacheStatus.Stale, timeout = 2.seconds.dilated)
  }

  private trait WithOkPayProcessor {
    def pollingInterval = 3.seconds.dilated
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
      invoice = "invoice",
      completed = true
    )
    val funds = ExchangeId.random()
    val cause = new Exception("Sample error") with NoStackTrace
    val client = new OkPayClientMock(senderAccount)
    var processor: ActorRef = _
    val properties = new DefaultPaymentProcessorProperties
    private val clientFactory = new ClientFactory {
      override def build(): OkPayClient = client
      override def shutdown(): Unit = {}
    }
    val fundsRegistry = new MockSupervisedActor()
    val processorProps = Props(new OkPayProcessorActor(
      clientFactory, fundsRegistry.props(), pollingInterval))
    val eventsProbe, requester = TestProbe()
    system.eventStream.subscribe(
      eventsProbe.ref, classOf[PaymentProcessorActor.FundsAvailabilityEvent])

    def givenPaymentProcessorCreated(): Unit = {
      processor = system.actorOf(processorProps)
    }

    def givenStartedPaymentProcessor(): Unit = {
      givenPaymentProcessorCreated()
      requester.send(processor, Service.Start {})
      requester.expectMsg(Service.Started)
    }

    def givenClientBalance(balance: FiatAmount): Unit = {
      client.setBalances(FiatAmounts.fromAmounts(balance))
    }

    def givenBalanceRetrievalFailure(): Unit = {
      client.setBalances(Future.failed(cause))
    }

    def givenClientPaymentWillSucceedWith(payment: Payment): Unit = {
      client.setPaymentResult(Future.successful(payment))
    }

    def givenClientPaymentWillFail(): Unit = {
      client.setPaymentResult(Future.failed(cause))
    }

    def givenClientExistingPayment(payment: Payment): Unit = {
      client.givenExistingPayment(payment)
    }

    def givenClientNonExistingPayment(payment: Payment): Unit = {
      client.givenNonExistingPayment(payment.id)
    }

    def givenClientPaymentCannotBeRetrieved(payment: Payment): Unit = {
      client.givenPaymentCannotBeRetrieved(payment.id, cause)
    }

    def whenBalanceIsRequested(currency: FiatCurrency): Unit = {
      requester.send(processor, PaymentProcessorActor.RetrieveBalance(currency))
    }

    def whenFundsBlockingIsRequested(request: PaymentProcessorActor.BlockFunds): Unit = {
      requester.send(processor, request)
    }

    def whenFundsUnblockingIsRequested(request: PaymentProcessorActor.UnblockFunds): Unit = {
      requester.send(processor, request)
    }

    def whenPaymentIsRequested(amount: FiatAmount): Unit = {
      requester.send(processor,
        PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment", "invoice"))
    }

    def whenFindPaymentIsRequested(payment: Payment): Unit = {
      requester.send(
        processor, PaymentProcessorActor.FindPayment(FindPaymentCriterion.ById(payment.id)))
    }

    def expectRegistryIsInitialized(): Unit = {
      fundsRegistry.expectCreation()
      fundsRegistry.expectMsgType[BlockedFiatRegistry.BalancesUpdate]
    }

    def expectBalancePropertyUpdated(
        balance: FiatAmount,
        cacheStatus: CacheStatus = CacheStatus.Fresh,
        timeout: FiniteDuration = 200.millis): Unit = {
      eventually(PatienceConfiguration.Timeout(timeout)) {
        properties.balances.get.status shouldBe cacheStatus
        properties.balances.get.cached(balance.currency) shouldBe balance
      }
    }

    def expectRetrieveBlockedFunds(funds: FiatAmount): Unit = {
      fundsRegistry.expectAskWithReply {
        case BlockedFiatRegistry.RetrieveTotalBlockedFunds(funds.currency) =>
          BlockedFiatRegistry.TotalBlockedFunds(funds)
      }
    }

    def expectRetrieveBlockedFunds(currency: FiatCurrency): Unit = {
      expectRetrieveBlockedFunds(currency(0))
    }

    def expectRegistryMarksUsed(amount: FiatAmount): Unit = {
      fundsRegistry.expectAskWithReply {
        case BlockedFiatRegistry.MarkUsed(`funds`, `amount`) =>
          BlockedFiatRegistry.FundsMarkedUsed(funds, amount)
      }
    }

    def expectRegistryCannotMarkUsed(): Unit = {
      fundsRegistry.expectAskWithReply {
        case BlockedFiatRegistry.MarkUsed(_, requested) =>
          BlockedFiatRegistry.CannotMarkUsed(funds, requested, "not enough!")
      }
    }

    def expectRegistryMarksUnused(amount: FiatAmount): Unit = {
      fundsRegistry.expectMsg(BlockedFiatRegistry.UnmarkUsed(funds, amount))
    }

    def expectBalanceRetrieved(balance: FiatAmount, blocked: FiatAmount): Unit = {
      requester.expectMsg(PaymentProcessorActor.BalanceRetrieved(balance, blocked))
    }

    def expectBalanceRetrieved(): Unit = {
      requester.expectMsgType[PaymentProcessorActor.BalanceRetrieved]
    }

    def expectBalanceRetrievalFailure(currency: FiatCurrency): Unit = {
      requester.expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(currency, cause))
    }

    def expectPaymentSuccess(amount: FiatAmount): Unit = {
      val response = requester.expectMsgType[PaymentProcessorActor.Paid].payment
      response.id shouldBe payment.id
      response.senderId shouldBe senderAccount
      response.receiverId shouldBe receiverAccount
      response.amount shouldBe amount
      response.description shouldBe "comment"
      response.invoice shouldBe "invoice"
    }

    def expectPaymentFailed(errorHint: String = ""): Unit = {
      requester.expectMsgPF() {
        case PaymentProcessorActor.PaymentFailed(_, ex) =>
          ex.toString should include (errorHint)
      }
    }

    def expectPaymentFound(payment: Payment): Unit = {
      requester.expectMsgPF() {
        case PaymentProcessorActor.PaymentFound(`payment`) =>
      }
    }

    def expectPaymentNotFound(payment: Payment): Unit = {
      requester.expectMsg(
        PaymentProcessorActor.PaymentNotFound(FindPaymentCriterion.ById(payment.id)))
    }

    def expectFindPaymentFailed(payment: Payment): Unit = {
      requester.expectMsg(
        PaymentProcessorActor.FindPaymentFailed(FindPaymentCriterion.ById(payment.id), cause))
    }

    def expectForwardedToRegistry(request: Any): Unit = {
      fundsRegistry.expectForward(request, requester.ref)
    }
  }

  private trait WithReadyOkPayProcessor extends WithOkPayProcessor {
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
  }
}
