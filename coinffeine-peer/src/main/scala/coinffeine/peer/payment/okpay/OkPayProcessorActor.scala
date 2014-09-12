package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._

import coinffeine.common.akka.{AskPattern, ServiceActor}
import coinffeine.model.currency.{Balance, CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.event.FiatBalanceChangeEvent
import coinffeine.model.payment.OkPayPaymentProcessor
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.event.EventPublisher
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.BlockingFundsActor._

class OkPayProcessorActor(
    accountId: AccountId,
    client: OkPayClient,
    pollingInterval: FiniteDuration)
  extends Actor with ActorLogging with ServiceActor[Unit] with EventPublisher {

  import context.dispatcher
  import OkPayProcessorActor._

  private val blockingFunds = context.actorOf(BlockingFundsActor.props, "blocking")
  private var currentBalances: Map[c forSome { type c <: FiatCurrency },
                                   Balance[c] forSome { type c <: FiatCurrency} ] = Map.empty

  private var timer: Cancellable = _

  override def starting(args: Unit) = {
    pollBalances()
    timer = context.system.scheduler.schedule(
      initialDelay = pollingInterval,
      interval = pollingInterval,
      receiver = self,
      message = PollBalances
    )
    becomeStarted(started)
  }

  override def stopping() = {
    Option(timer).foreach(_.cancel())
    becomeStopped()
  }

  private def started: Receive = {
    case PaymentProcessorActor.RetrieveAccountId =>
      sender ! RetrievedAccountId(accountId)
    case pay: Pay[_] =>
      sendPayment(sender(), pay)
    case FindPayment(paymentId) =>
      findPayment(sender(), paymentId)
    case RetrieveBalance(currency) =>
      currentBalance(sender(), currency)
    case PollBalances =>
      pollBalances()
    case UpdateBalances(balances) =>
      updateBalances(balances)
    case BalanceUpdateFailed(cause) =>
      notifyBalanceUpdateFailure(cause)
    case msg@(BlockFunds(_) | UnblockFunds(_)) =>
      blockingFunds.forward(msg)
  }

  private def sendPayment[C <: FiatCurrency](requester: ActorRef, pay: Pay[C]): Unit = {
    (for {
      _ <- useFunds(pay)
      payment <- client.sendPayment(pay.to, pay.amount, pay.comment)
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
    AskPattern(blockingFunds, request, "fail to use funds")
      .withImmediateReplyOrError[FundsUsed, CannotUseFunds](
        failure => throw new RuntimeException(failure.reason))
      .map(_ => {})
  }

  private def findPayment(requester: ActorRef, paymentId: PaymentId): Unit = {
    client.findPayment(paymentId).onComplete {
      case Success(Some(payment)) => requester ! PaymentFound(payment)
      case Success(None) => requester ! PaymentNotFound(paymentId)
      case Failure(error) => requester ! FindPaymentFailed(paymentId, error)
    }
  }

  private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
    val balances = client.currentBalances()
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
    AskPattern(blockingFunds, RetrieveBlockedFunds(currency))
      .withImmediateReply[BlockedFunds[C]]().map(_.funds)
  }

  private def updateBalances(balances: Seq[FiatAmount]): Unit = {
    for (amount <- balances) {
      updateBalance(Balance(amount, hasExpired = false))
    }
    blockingFunds ! BalancesUpdate(balances)
  }

  private def notifyBalanceUpdateFailure(cause: Throwable): Unit = {
    log.error(cause, "Cannot poll OKPay for balances")
    for (balance <- currentBalances.values) {
      updateBalance(balance.copy(hasExpired = true))
    }
  }

  private def updateBalance[C <: FiatCurrency](balance: Balance[C]): Unit = {
    if (currentBalances.get(balance.amount.currency) != Some(balance)) {
      publishEvent(FiatBalanceChangeEvent(balance))
      currentBalances += balance.amount.currency -> balance
    }
  }

  private def pollBalances(): Unit = {
    client.currentBalances().map(UpdateBalances.apply).recover {
      case NonFatal(cause) => BalanceUpdateFailed(cause)
    }.pipeTo(self)
  }
}

object OkPayProcessorActor {

  val Id = "OKPAY"

  /** Self-message sent to trigger OKPay API polling. */
  private case object PollBalances

  /** Self-message sent to update to the latest balances */
  private case class UpdateBalances(balances: Seq[FiatAmount])

  /** Self-message to report balance polling failures */
  private case class BalanceUpdateFailed(cause: Throwable)

  def props(settings: OkPaySettings) = {
    val client = new OkPayWebServiceClient(
      account = settings.userAccount,
      seedToken = settings.seedToken,
      baseAddressOverride = Some(settings.serverEndpoint)
    )
    Props(new OkPayProcessorActor(settings.userAccount, client, settings.pollingInterval))
  }
}
