package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.{ActorRef, Props}
import akka.testkit._
import org.scalatest.concurrent.{Eventually, PatienceConfiguration}

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.{OkPayPaymentProcessor, Payment}
import coinffeine.peer.payment.okpay.OkPayProcessorActor.ClientFactory
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry
import coinffeine.peer.payment.okpay.ws.OkPayWebServiceClient
import coinffeine.peer.payment.{MutablePaymentProcessorProperties, PaymentProcessorActor}

class OkPayProcessorActorTest extends AkkaSpec("OkPayTest") with Eventually {

  "OKPayProcessor" must "identify itself" in new WithOkPayProcessor {
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
    client.setBalances(Seq(nextAmount))
    requester.send(processor, PaymentProcessorActor.RetrieveBalance(UsDollar))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.RetrieveTotalBlockedFunds(UsDollar) =>
        BlockedFiatRegistry.TotalBlockedFunds(13.USD)
    }
    requester.expectMsgType[PaymentProcessorActor.BalanceRetrieved[FiatCurrency]]

    expectBalanceUpdate(nextAmount)
  }

  it must "report failure to get the current balance" in new WithOkPayProcessor {
    client.setBalances(Future.failed(cause))
    processor = system.actorOf(processorProps)
    requester.send(processor, Service.Start {})
    requester.expectMsg(Service.Started)
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
    client.setPaymentResult(Future.successful(payment))
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenPaymentProcessorIsInitialized(balances = Seq(amountPlusFee))
    requester.send(processor,
      PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment", "invoice"))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(`funds`, `amountPlusFee`) =>
        BlockedFiatRegistry.FundsUsed(funds, amountPlusFee)
    }
    val response = requester.expectMsgType[PaymentProcessorActor.Paid[_ <: FiatCurrency]].payment
    response.id shouldBe payment.id
    response.senderId shouldBe senderAccount
    response.receiverId shouldBe receiverAccount
    response.amount shouldBe amount
    response.description shouldBe "comment"
    response.invoice shouldBe "invoice"

    withClue("the fee has been taken into account") {
      requester.send(
        processor,
        PaymentProcessorActor.Pay(funds, receiverAccount, 0.01.USD, "comment", "invoice"))
      requester.expectMsgPF() {
        case PaymentProcessorActor.PaymentFailed(_, ex) =>
          ex.toString should include ("fail to use funds")
      }
    }
  }

  it must "require enough funds to send a payment" in new WithOkPayProcessor {
    givenPaymentProcessorIsInitialized(balances = Seq(amount))
    requester.send(processor,
      PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment", "invoice"))
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(_, requested) =>
        BlockedFiatRegistry.CannotUseFunds(funds, requested, "not enough!")
    }
    requester.expectMsgClass(classOf[PaymentProcessorActor.PaymentFailed[_]])
  }

  it must "report failure to send a payment" in new WithOkPayProcessor {
    client.setPaymentResult(Future.failed(cause))
    val amountPlusFee = OkPayPaymentProcessor.amountPlusFee(amount)
    givenPaymentProcessorIsInitialized(balances = Seq(amountPlusFee))
    val payRequest = PaymentProcessorActor.Pay(funds, receiverAccount, amount, "comment", "invoice")
    requester.send(processor, payRequest)
    fundsRegistry.expectAskWithReply {
      case BlockedFiatRegistry.UseFunds(`funds`, `amountPlusFee`) =>
        BlockedFiatRegistry.FundsUsed(funds, amountPlusFee)
    }
    requester.expectMsg(PaymentProcessorActor.PaymentFailed(payRequest, cause))
  }

  it must "be able to retrieve an existing payment" in new WithOkPayProcessor {
    client.givenExistingPayment(payment)
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsgType[PaymentProcessorActor.PaymentFound]
  }

  it must "be able to check a payment does not exist"  in new WithOkPayProcessor {
    client.givenNonExistingPayment(payment.id)
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsg(PaymentProcessorActor.PaymentNotFound(payment.id))
  }

  it must "report failure to retrieve a payment" in new WithOkPayProcessor {
    client.givenPaymentCannotBeRetrieved(payment.id, cause)
    givenPaymentProcessorIsInitialized()
    requester.send(processor, PaymentProcessorActor.FindPayment(payment.id))
    requester.expectMsg(PaymentProcessorActor.FindPaymentFailed(payment.id, cause))
  }

  it must "poll for EUR balance periodically" in new WithOkPayProcessor {
    override def pollingInterval = 1.second
    givenPaymentProcessorIsInitialized(balances = Seq(100.EUR))
    client.setBalances(Seq(120.EUR))
    expectBalanceUpdate(120.EUR, timeout = 2.seconds.dilated)
    client.setBalances(Seq(140.EUR))
    expectBalanceUpdate(140.EUR, timeout = 2.seconds.dilated)
    client.setBalances(Future.failed(new Exception("doesn't work")))
    expectBalanceUpdate(140.EUR, hasExpired = true, timeout = 2.seconds.dilated)
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
    val cause = new Exception("Sample error")
    val client = new OkPayClientMock(senderAccount)
    var processor: ActorRef = _
    val properties = new MutablePaymentProcessorProperties
    private val clientFactory = new ClientFactory {
      override def build(): OkPayClient = client
      override def shutdown(): Unit = {}
    }
    val fundsRegistry = new MockSupervisedActor()
    val processorProps = Props(new OkPayProcessorActor(
      clientFactory, fundsRegistry.props(), pollingInterval, properties))
    val eventsProbe, requester = TestProbe()
    system.eventStream.subscribe(
      eventsProbe.ref, classOf[PaymentProcessorActor.FundsAvailabilityEvent])

    def givenPaymentProcessorIsInitialized(balances: Seq[FiatAmount] = Seq.empty): Unit = {
      client.setBalances(balances)
      processor = system.actorOf(processorProps)
      fundsRegistry.expectCreation()
      requester.send(processor, Service.Start({}))
      requester.expectMsg(Service.Started)
      fundsRegistry.expectMsgType[BlockedFiatRegistry.BalancesUpdate]
    }

    def expectBalanceUpdate(balance: FiatAmount,
                            hasExpired: Boolean = false,
                            timeout: FiniteDuration = 200.millis): Unit = {
      eventually(PatienceConfiguration.Timeout(timeout)) {
        properties.balance(balance.currency) shouldBe FiatBalance(balance, hasExpired)
      }
    }
  }
}
