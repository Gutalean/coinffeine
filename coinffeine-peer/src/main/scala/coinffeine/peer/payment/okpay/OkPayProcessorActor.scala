package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._

import coinffeine.alarms.akka.EventStreamReporting
import coinffeine.common.ScalaFutureImplicits._
import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.{AskPattern, ServiceLifecycle}
import coinffeine.model.currency._
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.model.util.Cached
import coinffeine.peer.events.fiat.FiatBalanceChanged
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistryActor
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistryActor._

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
  private var balances = Cached.stale(FiatAmounts.empty)
  private var remainingLimits = Cached.stale(FiatAmounts.empty)

  override def onStart(args: Unit) = {
    pollAccountState()
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
    case pay: Pay => sendPayment(sender(), pay)
    case FindPayment(criterion) => findPayment(sender(), criterion)
    case RetrieveBalance(currency) => currentBalance(sender(), currency)
    case PollBalances => pollAccountState()
    case PollResult(newBalances, newRemainingLimits) =>
      updateCachedInformation(newBalances, newRemainingLimits)
    case CheckAccountExistence(accountId) => checkAccountExistence(accountId)
    case msg @ (_: BlockFunds | _: UnblockFunds) => registry.forward(msg)
  }

  private def sendPayment(requester: ActorRef, pay: Pay): Unit = {
    (for {
      _ <- markFundsUsed(pay)
      payment <- clientFactory.build().sendPayment(pay.to, pay.amount, pay.comment, pay.invoice)
    } yield payment).onComplete {
      case Success(payment) =>
        requester ! Paid(payment)
        pollAccountState()
      case Failure(error) =>
        unmarkFundsUsed(pay.fundsId, pay.amount)
        requester ! PaymentFailed(pay, error)
        pollAccountState()
    }
  }

  private def markFundsUsed(pay: Pay): Future[Unit] = {
    val request = MarkUsed(pay.fundsId, OkPayPaymentProcessor.amountPlusFee(pay.amount))
    log.debug("Using funds with id %s. Using %s for %s".format(
      pay.fundsId, request.amount, pay.invoice))
    AskPattern(registry, request, "fail to use funds")
      .withImmediateReplyOrError[FundsMarkedUsed, CannotMarkUsed](
        failure => throw new RuntimeException(failure.reason))
      .map(_ => {})
  }

  private def unmarkFundsUsed(
      fundsId: ExchangeId, amount: FiatAmount): Unit = {
    log.debug("Unmarking funds with id {} as used", fundsId)
    registry ! UnmarkUsed(fundsId, OkPayPaymentProcessor.amountPlusFee(amount))
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

  private def currentBalance(requester: ActorRef, currency: FiatCurrency): Unit = {
    val pollResult = pollAccountState()
    val blockedFundsResult = blockedFundsForCurrency(currency)
    val completeBalance = for {
      accountState <- pollResult
      balances <- Future.fromTry(accountState.balances)
      totalAmount = balances.get(currency).getOrElse(throw new PaymentProcessorException(
        s"No balance in $currency"))
      blockedAmount <- blockedFundsResult
    } yield BalanceRetrieved(totalAmount, blockedAmount)
    completeBalance.recover {
      case NonFatal(error) => BalanceRetrievalFailed(currency, error)
    }.pipeTo(requester)
  }

  private def blockedFundsForCurrency(currency: FiatCurrency): Future[FiatAmount] = {
    AskPattern(registry, RetrieveTotalBlockedFunds)
      .withImmediateReply[TotalBlockedFunds]()
        .map(_.funds.get(currency).getOrElse(currency.zero))
  }

  private def updateCachedInformation(
      maybeBalances: Try[FiatAmounts], maybeLimits: Try[FiatAmounts]): Unit = {

    if (maybeBalances.isSuccess && maybeLimits.isSuccess) recover(OkPayPollingAlarm)
    else alert(OkPayPollingAlarm)

    maybeBalances match {
      case Success(newBalances) =>
        balances = Cached.fresh(newBalances)
      case Failure(cause) =>
        log.error(cause, "Cannot poll OkPay for balances")
        balances = Cached.stale(balances.cached)
    }

    maybeLimits match {
      case Success(newLimits) =>
        remainingLimits = Cached.fresh(newLimits)
      case Failure(cause) =>
        log.error(cause, "Cannot poll OkPay for remaining limits")
        remainingLimits = Cached.stale(remainingLimits.cached)
    }

    notifyAccountStatus()
  }

  private def notifyAccountStatus(): Unit = {
    registry ! AccountUpdate(balances.cached, remainingLimits.cached)
    publish(FiatBalanceChanged(for {
      cachedAmounts <- balances
      cachedRemainingLimits <- remainingLimits
    } yield FiatBalance(cachedAmounts, cachedRemainingLimits)))
  }

  private def checkAccountExistence(accountId: AccountId): Unit = {
    clientFactory.build()
        .checkExistence(accountId)
        .map { exists => if (exists) AccountExistence.Existing else AccountExistence.NonExisting }
        .recover { case NonFatal(ex) =>
          log.error(ex, "Cannot check account '{}' existence", accountId)
          AccountExistence.CannotCheck
        }
        .pipeTo(sender())
  }

  private def pollAccountState(): Future[PollResult] = {
    val balancesResult = clientFactory.build().currentBalances().materialize
    val limitsResult = clientFactory.build().currentRemainingLimits().materialize
    val pollResult = for {
      balances <- balancesResult
      limits <- limitsResult
    } yield PollResult(balances, limits)
    pollResult.pipeTo(self)
    pollResult
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

  /** Self-message sent to update to the latest balances and remaining limits */
  private case class PollResult(balances: Try[FiatAmounts], remainingLimits: Try[FiatAmounts])

  def props(lookupSettings: () => OkPaySettings) = {
    Props(new OkPayProcessorActor(new OkPayClientFactory(lookupSettings), BlockedFiatRegistryActor.props,
      lookupSettings().pollingInterval))
  }
}
