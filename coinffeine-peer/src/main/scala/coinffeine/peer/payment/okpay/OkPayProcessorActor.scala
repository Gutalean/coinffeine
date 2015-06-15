package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._

import coinffeine.alarms.akka.EventStreamReporting
import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.{AskPattern, ServiceLifecycle}
import coinffeine.model.currency._
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.peer.events.fiat.BalanceChanged
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry._

private class OkPayProcessorActor(
    clientFactory: OkPayProcessorActor.ClientFactory,
    registryProps: Props,
    pollingInterval: FiniteDuration)
  extends Actor with ActorLogging with ServiceLifecycle[Unit]
  with EventStreamReporting with CoinffeineEventProducer {

  import context.dispatcher
  import OkPayProcessorActor._

  private val registry = context.actorOf(registryProps, "funds")

  private var timer: Cancellable = _
  private var balances = Map.empty[FiatCurrency, AnyFiatBalance]

  override def onStart(args: Unit) = {
    pollBalances()
    timer = context.system.scheduler.schedule(
      initialDelay = pollingInterval,
      interval = pollingInterval,
      receiver = self,
      message = PollBalances
    )
    BecomeStarted(started)
  }

  override def onStop() = {
    Option(timer).foreach(_.cancel())
    clientFactory.shutdown()
    BecomeStopped
  }

  private def started: Receive = {
    case PaymentProcessorActor.RetrieveAccountId =>
      sender ! RetrievedAccountId(clientFactory.build().accountId)
    case pay: Pay[_] =>
      sendPayment(sender(), pay)
    case FindPayment(criterion) =>
      findPayment(sender(), criterion)
    case RetrieveBalance(currency) =>
      currentBalance(sender(), currency)
    case PollBalances =>
      pollBalances()
    case UpdateBalances(newBalances) =>
      updateBalances(newBalances)
    case BalanceUpdateFailed(cause) =>
      notifyBalanceUpdateFailure(cause)
    case msg @ (_: BlockFunds | _: UnblockFunds) =>
      registry.forward(msg)
  }

  private def sendPayment[C <: FiatCurrency](requester: ActorRef, pay: Pay[C]): Unit = {
    (for {
      _ <- useFunds(pay)
      payment <- clientFactory.build().sendPayment(pay.to, pay.amount, pay.comment, pay.invoice)
    } yield payment).onComplete {
      case Success(payment) =>
        requester ! Paid(payment)
        pollBalances()
      case Failure(error) =>
        requester ! PaymentFailed(pay, error)
        pollBalances()
    }
  }

  private def useFunds[C <: FiatCurrency](pay: Pay[C]): Future[Unit] = {
    val request = UseFunds(pay.fundsId, OkPayPaymentProcessor.amountPlusFee(pay.amount))
    log.debug(s"Using funds with id ${pay.fundsId}. " +
      s"Amount to use: ${OkPayPaymentProcessor.amountPlusFee(pay.amount)}")
    AskPattern(registry, request, "fail to use funds")
      .withImmediateReplyOrError[FundsMarkedUsed, CannotMarkUsed](
        failure => throw new RuntimeException(failure.reason))
      .map(_ => {})
  }

  private def findPayment(requester: ActorRef, criterion: FindPaymentCriterion): Unit = {
    (criterion match {
      case FindPaymentCriterion.ById(paymentId) =>
        clientFactory.build().findPaymentById(paymentId)
      case FindPaymentCriterion.ByInvoice(invoice) =>
        clientFactory.build().findPaymentByInvoice(invoice)
    }).onComplete {
      case Success(Some(payment)) => requester ! PaymentFound(payment)
      case Success(None) => requester ! PaymentNotFound(criterion)
      case Failure(error) => requester ! FindPaymentFailed(criterion, error)
    }
  }

  private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
    val balances = clientFactory.build().currentBalances()
    balances.onSuccess { case b =>
      self ! UpdateBalances(b)
    }
    val blockedFunds = blockedFundsForCurrency(currency)
    (for {
      totalAmount <- balances.map { b =>
        b.find(_.currency == currency)
          .getOrElse(throw new PaymentProcessorException(s"No balance in $currency"))
          .asInstanceOf[CurrencyAmount[C]]
      }
      blockedAmount <- blockedFunds
    } yield BalanceRetrieved(totalAmount, blockedAmount)).recover {
      case NonFatal(error) => BalanceRetrievalFailed(currency, error)
    }.pipeTo(requester)
  }

  private def blockedFundsForCurrency[C <: FiatCurrency](currency: C): Future[CurrencyAmount[C]] = {
    AskPattern(registry, RetrieveTotalBlockedFunds(currency))
      .withImmediateReply[TotalBlockedFunds[C]]().map(_.funds)
  }

  private def updateBalances(balances: Seq[FiatAmount]): Unit = {
    recover(OkPayPollingAlarm)
    for (amount <- balances) {
      updateBalance(FiatBalance(amount, hasExpired = false))
    }
    registry ! BalancesUpdate(balances)
  }

  private def notifyBalanceUpdateFailure(cause: Throwable): Unit = {
    log.error(cause, "Cannot poll OKPay for balances")
    alert(OkPayPollingAlarm)
    for (balance <- balances.values) {
      updateBalance(balance.copy(hasExpired = true))
    }
  }

  private def updateBalance[C <: FiatCurrency](balance: FiatBalance[C]): Unit = {
    balances += balance.amount.currency -> balance
    publish(BalanceChanged(balance))
  }

  private def pollBalances(): Unit = {
    clientFactory.build().currentBalances().map(UpdateBalances.apply).recover {
      case NonFatal(cause) => BalanceUpdateFailed(cause)
    }.pipeTo(self)
  }
}

object OkPayProcessorActor {

  val Id = "OKPAY"

  trait ClientFactory {
    def build(): OkPayClient
    def shutdown(): Unit
  }

  /** Self-message sent to trigger OKPay API polling. */
  private case object PollBalances

  /** Self-message sent to update to the latest balances */
  private case class UpdateBalances(balances: Seq[FiatAmount])

  /** Self-message to report balance polling failures */
  private case class BalanceUpdateFailed(cause: Throwable)

  def props(lookupSettings: () => OkPaySettings) = {
    Props(new OkPayProcessorActor(new OkPayClientFactory(lookupSettings), BlockedFiatRegistry.props,
      lookupSettings().pollingInterval))
  }
}
