package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.common.properties.Property
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.Payment
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.model.util.{Cached, CacheStatus}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.FindPaymentCriterion
import coinffeine.peer.payment.okpay.OkPayProcessorActor.ClientFactory
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry
import coinffeine.peer.payment.okpay.ws.OkPayWebServiceClient
import coinffeine.peer.properties.fiat.DefaultPaymentProcessorProperties

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with Eventually {

  "OKPayProcessor" must "be able to get the current balance" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenBalanceIsRequested(UsDollar)
    expectRetrieveBlockedFunds(13.USD)
    expectBalanceRetrieved(amount, 13.USD)
  }

  it must "update properties when asked to get the current balance" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    givenAccountBalance(amount * 2)
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
    givenAccountBalance(amountPlusFee)
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
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    expectRegistryIsInitialized()
    whenPaymentIsRequested(amount)
    expectRegistryCannotMarkUsed()
    expectPaymentFailed("fail to use funds")
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    givenClientPaymentWillFail()
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenAccountBalance(amountPlusFee)
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
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectPaymentNotFound(payment)
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    givenPaymentsCannotBeRetrieved()
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectFindPaymentFailed(payment)
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenAccountBalance(100.EUR)
    givenStartedPaymentProcessor()

    givenAccountBalance(120.EUR)
    expectBalancePropertyUpdated(120.EUR)

    givenAccountBalance(140.EUR)
    expectBalancePropertyUpdated(140.EUR)

    givenBalanceRetrievalFailure()
    expectBalancePropertyUpdated(140.EUR, cacheStatus = CacheStatus.Stale)
  }

  it must "poll for remaining limits periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenAccountBalance(100.EUR)
    givenStartedPaymentProcessor()

    givenAccountRemainingLimits(300.EUR)
    expectRemainingLimitsPropertyUpdated(300.EUR)

    givenAccountRemainingLimits(280.EUR)
    expectRemainingLimitsPropertyUpdated(280.EUR)

    givenRemainingLimitsRetrievalFailure()
    expectRemainingLimitsPropertyUpdated(280.EUR, cacheStatus = CacheStatus.Stale)
  }

  private trait WithOkPayProcessor {
    def pollingInterval = 3.seconds.dilated
    val propertyCheckTimeout = PatienceConfiguration.Timeout(2.seconds)
    val senderAccount = "OK12345"
    val receiverAccount = "OK54321"
    val amount = 100.USD
    val payment = Payment(
      id = "250092",
      senderId = senderAccount,
      receiverId = receiverAccount,
      amount = amount,
      fee = 0.USD,
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

    def givenAccountBalance(balance: FiatAmount): Unit = {
      client.givenBalances(FiatAmounts.fromAmounts(balance))
    }

    def givenAccountRemainingLimits(remainingLimit: FiatAmount): Unit = {
      client.givenLimits(FiatAmounts.fromAmounts(remainingLimit))
    }

    def givenBalanceRetrievalFailure(): Unit = {
      client.givenBalancesCannotBeRetrieved(cause)
    }

    def givenRemainingLimitsRetrievalFailure(): Unit = {
      client.givenLimitsCannotBeRetrieved(cause)
    }

    def givenClientPaymentWillSucceedWith(payment: Payment): Unit = {
      client.givenPaymentResult(Future.successful(payment))
    }

    def givenClientPaymentWillFail(): Unit = {
      client.givenPaymentResult(Future.failed(cause))
    }

    def givenClientExistingPayment(payment: Payment): Unit = {
      client.givenExistingPayment(payment)
    }

    def givenPaymentsCannotBeRetrieved(): Unit = {
      client.givenPaymentsCannotBeRetrieved(cause)
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
      fundsRegistry.expectMsgType[BlockedFiatRegistry.AccountUpdate]
    }

    def expectBalancePropertyUpdated(
        balance: FiatAmount,
        cacheStatus: CacheStatus = CacheStatus.Fresh): Unit = {
      expectPropertyUpdated(
        property = properties.balances,
        expectedValue = Cached(FiatAmounts.fromAmounts(balance), cacheStatus)
      )
    }

    def expectRemainingLimitsPropertyUpdated(
        remainingLimit: FiatAmount,
        cacheStatus: CacheStatus = CacheStatus.Fresh): Unit = {
      expectPropertyUpdated(
        property = properties.remainingLimits,
        expectedValue = Cached(FiatAmounts.fromAmounts(remainingLimit), cacheStatus)
      )
    }

    private def expectPropertyUpdated[A](property: Property[A], expectedValue: A): Unit = {
      eventually(propertyCheckTimeout) {
        property.get shouldBe expectedValue
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
