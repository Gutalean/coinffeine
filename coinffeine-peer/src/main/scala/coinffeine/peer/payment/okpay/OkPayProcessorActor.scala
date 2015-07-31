package coinffeine.peer.payment.okpay

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.pattern._
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}

import coinffeine.alarms.akka.EventStreamReporting
import coinffeine.common.ScalaFutureImplicits._
import coinffeine.common.akka.Service
import coinffeine.common.akka.event.CoinffeineEventProducer
import coinffeine.common.akka.persistence.{PeriodicSnapshot, PersistentEvent}
import coinffeine.model.currency._
import coinffeine.model.currency.balance.FiatBalance
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.payment.okpay.OkPayPaymentProcessor
import coinffeine.model.util.Cached
import coinffeine.peer.events.fiat.FiatBalanceChanged
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.blocking.BlockedFiatRegistry._
import coinffeine.peer.payment.okpay.blocking.{BlockedFiatRegistry, BlockedFiatRegistryImpl}

private class OkPayProcessorActor(
    override val persistenceId: String,
    clientFactory: OkPayProcessorActor.ClientFactory,
    registry: BlockedFiatRegistry,
    pollingInterval: FiniteDuration) extends PersistentActor
  with PeriodicSnapshot
  with ActorLogging
  with EventStreamReporting
  with CoinffeineEventProducer {

  import context.dispatcher

  import OkPayProcessorActor._

  private var timer: Cancellable = _
  private var balances = Cached.stale(FiatAmounts.empty)
  private var remainingLimits = Cached.stale(FiatAmounts.empty)
  private val notifier = new AvailabilityNotifier(context.system.eventStream)

  override def receiveRecover: Receive = {
    case event: FundsBlockedEvent => onFundsBlocked(event)
    case event: FundsMarkedUsedEvent => onFundsMarkedUsed(event)
    case event: FundsUnmarkedUsedEvent => onFundsUnmarkedUsed(event)
    case event: FundsUnblockedEvent => onFundsUnblocked(event)
    case SnapshotOffer(metadata, snapshot: Snapshot) =>
      setLastSnapshot(metadata.sequenceNr)
      restoreSnapshot(snapshot)
    case RecoveryCompleted =>
      publishFiatBalance()
      registry.notifyAvailabilityChanges(notifier)
  }

  override def receiveCommand: Receive = {
    case Service.Start(_) => start()
  }

  private def started: Receive = {
    case pay: Pay => markFundsUsed(pay, sender())
    case FundsMarkedUsed(pay, amount, requester) => sendPayment(pay, amount, requester)
    case UnmarkFundsUsed(fundsId, amount) => unmarkFundsUsed(fundsId, amount)

    case BlockFunds(fundsId, _) if registry.contains(fundsId) =>
      sender() ! AlreadyBlockedFunds(fundsId)
    case BlockFunds(fundsId, amount) =>
      persist(FundsBlockedEvent(fundsId, amount)) { event =>
        sender() ! BlockedFunds(fundsId)
        onFundsBlocked(event)
        publishFiatBalance()
      }
    case UnblockFunds(fundsId) =>
      persist(FundsUnblockedEvent(fundsId)) { event =>
        sender() ! UnblockFunds(fundsId)
        onFundsUnblocked(event)
        publishFiatBalance()
      }

    case FindPayment(criterion) => findPayment(sender(), criterion)
    case RetrieveBalance(currency) => currentBalance(sender(), currency)
    case PollBalances => pollAccountState()
    case PollResult(newBalances, newRemainingLimits) =>
      updateCachedInformation(newBalances, newRemainingLimits)
    case CheckAccountExistence(accountId) => checkAccountExistence(accountId)

    case Service.Stop => stop()
  }

  private def start() = {
    pollAccountState()
    timer = context.system.scheduler.schedule(
      initialDelay = pollingInterval,
      interval = pollingInterval,
      receiver = self,
      message = PollBalances
    )
    context.become(started)
    sender() ! Service.Started
  }

  private def stop(): Unit = {
    Option(timer).foreach(_.cancel())
    clientFactory.shutdown()
    context.become(Map.empty)
    sender() ! Service.Stopped
  }

  private def onFundsBlocked(event: FundsBlockedEvent): Unit = {
    registry.block(event.fundsId, event.amount)
    notifyAvailabilityUnlessRecovering()
  }

  private def onFundsUnblocked(event: FundsUnblockedEvent): Unit = {
    registry.unblock(event.fundsId)
    notifyAvailabilityUnlessRecovering()
  }

  private def onFundsMarkedUsed(event: FundsMarkedUsedEvent): Unit = {
    registry.markUsed(event.fundsId, event.amount)
    notifyAvailabilityUnlessRecovering()
  }

  private def onFundsUnmarkedUsed(event: FundsUnmarkedUsedEvent): Unit = {
    registry.unmarkUsed(event.fundsId, event.amount)
    notifyAvailabilityUnlessRecovering()
  }

  override protected def createSnapshot: Option[PersistentEvent] =
    Some(Snapshot(registry.takeMemento))

  private def restoreSnapshot(snapshot: Snapshot): Unit = {
    registry.restoreMemento(snapshot.funds)
    notifyAvailabilityUnlessRecovering()
  }

  private def notifyAvailabilityUnlessRecovering(): Unit = {
    if (recoveryFinished) {
      registry.notifyAvailabilityChanges(notifier)
    }
  }

  private def markFundsUsed(pay: Pay, requester: ActorRef): Unit = {
    val amount = OkPayPaymentProcessor.amountPlusFee(pay.amount)
    log.debug("Using funds with id %s. Using %s for '%s'".format(
      pay.fundsId, amount, pay.invoice))
    registry.canMarkUsed(pay.fundsId, amount).fold(
      succ = funds => persist(FundsMarkedUsedEvent(pay.fundsId, amount)) { event =>
        onFundsMarkedUsed(event)
        self ! FundsMarkedUsed(pay, amount, requester)
        publishFiatBalance()
      },
      fail = reason => requester ! PaymentFailed(pay, new Exception(reason))
    )
  }

  private def sendPayment(pay: Pay, amount: FiatAmount, requester: ActorRef): Unit = {
    clientFactory.build()
        .sendPayment(pay.to, pay.amount, pay.comment, pay.invoice)
        .onComplete {
          case Success(payment) =>
            requester ! Paid(payment)
            pollAccountState()
          case Failure(error) =>
            self ! UnmarkFundsUsed(pay.fundsId, amount)
            requester ! PaymentFailed(pay, error)
            pollAccountState()
        }
  }

  private def unmarkFundsUsed(
      fundsId: ExchangeId, amount: FiatAmount): Unit = {
    log.debug("Unmarking funds with id {} as used", fundsId)
    registry.canUnmarkUsed(fundsId, amount).fold(
      succ = _ => persist(FundsUnmarkedUsedEvent(fundsId, amount)) { event =>
        onFundsUnmarkedUsed(event)
        publishFiatBalance()
      },
      fail = reason => log.warning("cannot unmark funds {}: {}", fundsId, reason)
    )
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
    val blockedAmount = blockedFundsForCurrency(currency)
    val completeBalance = for {
      accountState <- pollResult
      balances <- Future.fromTry(accountState.balances)
      totalAmount = balances.get(currency).getOrElse(throw new PaymentProcessorException(
        s"No balance in $currency"))
    } yield BalanceRetrieved(totalAmount, blockedAmount)
    completeBalance.recover {
      case NonFatal(error) => BalanceRetrievalFailed(currency, error)
    }.pipeTo(requester)
  }

  private def blockedFundsForCurrency(currency: FiatCurrency): FiatAmount =
    registry.blockedFundsByCurrency
      .get(currency)
      .getOrElse(currency.zero)

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

    registry.updateTransientAmounts(balances.cached, remainingLimits.cached)
    notifyAvailabilityUnlessRecovering()
    publishFiatBalance()
  }

  private def publishFiatBalance(): Unit = {
    publish(FiatBalanceChanged(for {
      cachedAmounts <- balances
      cachedRemainingLimits <- remainingLimits
    } yield FiatBalance(cachedAmounts, registry.blockedFundsByCurrency, cachedRemainingLimits)))
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
  val PersistenceId = "blockedFiatRegistry"
  val Id = "OKPAY"

  trait ClientFactory {
    def build(): OkPayClient
    def shutdown(): Unit
  }

  /** Self-message to signal that the funds of a payment have been marked used and the payment
    * can proceed
    */
  private case class FundsMarkedUsed(pay: Pay, amount: FiatAmount, requester: ActorRef)

  /** Self-message to signal that the payment has failed and the funds should be unmarked */
  private case class UnmarkFundsUsed(funds: ExchangeId, amount: FiatAmount)

  /** Self-message sent to trigger OKPay API polling. */
  private case object PollBalances

  /** Self-message sent to update to the latest balances and remaining limits */
  private case class PollResult(balances: Try[FiatAmounts], remainingLimits: Try[FiatAmounts])

  def props(lookupSettings: () => OkPaySettings) = Props(new OkPayProcessorActor(
    persistenceId = PersistenceId,
    clientFactory = new OkPayClientFactory(lookupSettings),
    registry = new BlockedFiatRegistryImpl,
    pollingInterval = lookupSettings().pollingInterval
  ))
}
