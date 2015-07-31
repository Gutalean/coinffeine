package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}

import coinffeine.common.akka.Service
import coinffeine.common.akka.persistence.PeriodicSnapshot
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.properties.Property
import coinffeine.model.currency._
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.model.payment.{Payment, TestPayment}
import coinffeine.model.util.{CacheStatus, Cached}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.peer.payment.PaymentProcessorActor.{AccountExistence, CheckAccountExistence, FindPaymentCriterion}
import coinffeine.peer.payment.okpay.OkPayProcessorActor.ClientFactory
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistryImpl
import coinffeine.peer.properties.fiat.DefaultPaymentProcessorProperties

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with Eventually {

  "OKPayProcessor" should "be able to get the current balance" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    expectBalanceRetrieved(amount, 0.USD)
  }

  it should "update properties when asked to get the current balance" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    givenAccountBalance(amount * 2)
    whenBalanceIsRequested(UsDollar)
    expectBalancePropertyUpdated(amount * 2)
  }

  it should "report failure to get the current balance" in new WithOkPayProcessor {
    givenBalanceRetrievalFailure()
    givenStartedPaymentProcessor()
    expectBalanceRetrievalFailure(UsDollar)
  }

  it should "block funds" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    whenFundsBlockingIsRequested(funds, amount / 2)
    expectFundsAreBlocked(funds)
  }

  it should "unblock funds" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(amount / 2)

    whenFundsUnblockingIsRequested(funds)
    expectFundsAreUnblocked(funds)
  }

  it should "be able to send a payment that gets reserved funds reduced" in new WithOkPayProcessor {
    givenClientPaymentWillSucceedWith(payment)
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenAccountBalance(amountPlusFee)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(amountPlusFee)
    whenPaymentIsRequested(amount)
    expectPaymentSuccess(amount)
  }

  it should "require enough funds to send a payment" in new WithOkPayProcessor {
    givenAccountBalance(amount)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(amount / 2)
    whenPaymentIsRequested(amount)
    expectPaymentFailed("insufficient blocked funds")
  }

  it should "report failure to send a payment" in new WithOkPayProcessor {
    givenClientPaymentWillFail()
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenAccountBalance(amountPlusFee)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(amountPlusFee)

    whenPaymentIsRequested(amount)

    expectPaymentFailed()
    expectBalanceRetrieved(amountPlusFee, amountPlusFee)
  }

  it should "be able to retrieve an existing payment" in new WithOkPayProcessor {
    givenClientExistingPayment(payment)
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectPaymentFound(payment)
  }

  it should "be able to check a payment does not exist" in new WithOkPayProcessor {
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectPaymentNotFound(payment)
  }

  it should "report failure to retrieve a payment" in new WithOkPayProcessor {
    givenPaymentsCannotBeRetrieved()
    givenStartedPaymentProcessor()
    whenFindPaymentIsRequested(payment)
    expectFindPaymentFailed(payment)
  }

  it should "poll for EUR balance periodically" in new WithOkPayProcessor {
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

  it should "poll for remaining limits periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenAccountBalance(100.EUR)
    givenStartedPaymentProcessor()

    givenAccountRemainingLimits(300.EUR)
    expectBalancePropertyUpdated(100.EUR, Some(300.EUR))

    givenAccountRemainingLimits(280.EUR)
    expectBalancePropertyUpdated(100.EUR, Some(280.EUR))

    givenRemainingLimitsRetrievalFailure()
    expectBalancePropertyUpdated(100.EUR, Some(280.EUR), cacheStatus = CacheStatus.Stale)
  }

  it should "check that an account actually exists" in new WithOkPayProcessor {
    val existingAccount = "existingAccount"
    givenAccountExists(existingAccount)
    givenStartedPaymentProcessor()
    expectAccountExistence(existingAccount, AccountExistence.Existing)
  }

  it should "check that an account doesn't exist" in new WithOkPayProcessor {
    val madeUpAccount = "madeUpAccount"
    givenAccountDoesNotExist(madeUpAccount)
    givenStartedPaymentProcessor()
    expectAccountExistence(madeUpAccount, AccountExistence.NonExisting)
  }

  it should "report failure to check the existence of an account" in new WithOkPayProcessor {
    val someAccount = "someAccount"
    givenAccountExistenceCheckWillFail(someAccount)
    givenStartedPaymentProcessor()
    expectAccountExistence(someAccount, AccountExistence.CannotCheck)
  }

  it should "persist its state" in new WithOkPayProcessor {
    givenAccountBalance(100.USD)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(50.USD)
    givenStoppedPaymentProcessor()
  }

  it should "recover its previous state" in new WithOkPayProcessor {
    override protected def reusePersistenceId = true
    givenAccountBalance(100.USD)
    givenStartedPaymentProcessor()
    expectBalanceRetrieved(100.USD, 50.USD)
  }

  it should "persist its state in a snapshot" in new WithOkPayProcessor {
    givenAccountBalance(100.USD)
    givenStartedPaymentProcessor()
    givenFundsAreBlocked(50.USD)
    processor ! PeriodicSnapshot
    givenStoppedPaymentProcessor()
  }

  it should "recover its previous state from snapshot" in new WithOkPayProcessor {
    override protected def reusePersistenceId = true
    givenAccountBalance(100.USD)
    givenStartedPaymentProcessor()
    expectBalanceRetrieved(100.USD, 50.USD)
  }

  private var currentPersistenceId = 1

  private trait WithOkPayProcessor {
    protected def reusePersistenceId: Boolean = false
    def pollingInterval = 3.seconds.dilated
    val propertyCheckTimeout = PatienceConfiguration.Timeout(2.seconds)
    val amount = 100.USD
    val payment = TestPayment.random(
      netAmount = amount,
      fee = 0.USD,
      invoice = "invoice",
      completed = true
    )
    val senderAccount = payment.senderId
    val receiverAccount = payment.receiverId
    val funds = ExchangeId.random()
    val cause = new Exception("Sample error") with NoStackTrace
    val client = new OkPayClientMock(senderAccount)
    var processor: ActorRef = _
    val properties = new DefaultPaymentProcessorProperties
    private val clientFactory = new ClientFactory {
      override def build(): OkPayClient = client
      override def shutdown(): Unit = {}
    }
    protected val registry = new BlockedFiatRegistryImpl()
    private val persistenceId = {
      if (!reusePersistenceId) {
        currentPersistenceId += 1
      }
      currentPersistenceId.toString
    }
    val processorProps = Props(new OkPayProcessorActor(persistenceId,
      clientFactory, registry, pollingInterval))
    val eventsProbe, requester = TestProbe()
    system.eventStream.subscribe(
      eventsProbe.ref, classOf[PaymentProcessorActor.FundsAvailabilityEvent])

    def givenPaymentProcessorCreated(): Unit = {
      processor = system.actorOf(processorProps)
      watch(processor)
    }

    def givenStartedPaymentProcessor(): Unit = {
      givenPaymentProcessorCreated()
      requester.send(processor, Service.Start {})
      requester.expectMsg(Service.Started)
    }

    def givenStoppedPaymentProcessor(): Unit = {
      requester.send(processor, Service.Stop)
      requester.expectMsg(Service.Stopped)
      system.stop(processor)
      expectTerminated(processor)
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

    def givenAccountExists(accountId: AccountId): Unit = {
      client.givenAccountExistence(accountId, AccountExistence.Existing)
    }

    def givenAccountDoesNotExist(accountId: AccountId): Unit = {
      client.givenAccountExistence(accountId, AccountExistence.NonExisting)
    }

    def givenAccountExistenceCheckWillFail(accountId: AccountId): Unit = {
      client.givenAccountExistence(accountId, AccountExistence.CannotCheck)
    }

    def givenFundsAreBlocked(amount: FiatAmount): Unit = {
      givenFundsAreBlocked(funds, amount)
    }

    def givenFundsAreBlocked(funds: ExchangeId, amount: FiatAmount): Unit = {
      whenFundsBlockingIsRequested(funds, amount)
      expectFundsAreBlocked(funds)
    }

    def whenBalanceIsRequested(currency: FiatCurrency): Unit = {
      requester.send(processor, PaymentProcessorActor.RetrieveBalance(currency))
    }

    def whenFundsBlockingIsRequested(funds: ExchangeId, amount: FiatAmount): Unit = {
      requester.send(processor, PaymentProcessorActor.BlockFunds(funds, amount))
    }

    def whenFundsUnblockingIsRequested(funds: ExchangeId): Unit = {
      requester.send(processor, PaymentProcessorActor.UnblockFunds(funds))
    }

    def whenPaymentIsRequested(amount: FiatAmount): Unit = {
      requester.send(processor,
        PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment", "invoice"))
    }

    def whenFindPaymentIsRequested(payment: Payment): Unit = {
      requester.send(
        processor, PaymentProcessorActor.FindPayment(FindPaymentCriterion.ById(payment.paymentId)))
    }

    def expectFundsAreBlocked(funds: ExchangeId): Unit = {
      requester.expectMsg(PaymentProcessorActor.BlockedFunds(funds))
    }

    def expectFundsAreUnblocked(funds: ExchangeId): Unit = {
      requester.expectMsg(PaymentProcessorActor.UnblockFunds(funds))
    }

    def expectBalancePropertyUpdated(
        balance: FiatAmount,
        remainingLimit: Option[FiatAmount] = None,
        cacheStatus: CacheStatus = CacheStatus.Fresh): Unit = {
      expectPropertyUpdated(
        property = properties.balances,
        expectedValue = Cached(
          FiatBalance(
            amounts = FiatAmounts.fromAmounts(balance),
            remainingLimits = FiatAmounts.fromAmounts(remainingLimit.toSeq: _*)
          ),
          cacheStatus
        )
      )
    }

    private def expectPropertyUpdated[A](property: Property[A], expectedValue: A): Unit = {
      eventually(propertyCheckTimeout) {
        property.get shouldBe expectedValue
      }
    }

    def expectBalanceRetrieved(balance: FiatAmount, blocked: FiatAmount): Unit = {
      whenBalanceIsRequested(UsDollar)
      requester.expectMsg(PaymentProcessorActor.BalanceRetrieved(balance, blocked))
    }

    def expectBalanceRetrieved(): Unit = {
      whenBalanceIsRequested(UsDollar)
      requester.expectMsgType[PaymentProcessorActor.BalanceRetrieved]
    }

    def expectBalanceRetrievalFailure(currency: FiatCurrency): Unit = {
      whenBalanceIsRequested(UsDollar)
      requester.expectMsg(PaymentProcessorActor.BalanceRetrievalFailed(currency, cause))
    }

    def expectPaymentSuccess(amount: FiatAmount): Unit = {
      val response = requester.expectMsgType[PaymentProcessorActor.Paid].payment
      response.paymentId shouldBe payment.paymentId
      response.senderId shouldBe senderAccount
      response.receiverId shouldBe receiverAccount
      response.netAmount shouldBe amount
      response.description shouldBe payment.description
      response.invoice shouldBe payment.invoice
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
        PaymentProcessorActor.PaymentNotFound(FindPaymentCriterion.ById(payment.paymentId)))
    }

    def expectFindPaymentFailed(payment: Payment): Unit = {
      requester.expectMsg(
        PaymentProcessorActor.FindPaymentFailed(FindPaymentCriterion.ById(payment.paymentId), cause))
    }

    def expectAccountExistence(
        accountId: AccountId, expectedExistence: AccountExistence): Unit = {
      requester.send(processor, CheckAccountExistence(accountId))
      requester.expectMsg(expectedExistence)
    }
  }
}
