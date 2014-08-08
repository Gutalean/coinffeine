package coinffeine.peer.payment.okpay

import java.net.URI
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern._
import com.typesafe.config.Config

import coinffeine.common.akka.AskPattern
import coinffeine.model.currency.{CurrencyAmount, FiatAmount, FiatCurrency}
import coinffeine.model.payment.PaymentProcessor._
import coinffeine.peer.api.event.FiatBalanceChangeEvent
import coinffeine.peer.event.EventProducer
import coinffeine.peer.payment.PaymentProcessorActor._
import coinffeine.peer.payment._
import coinffeine.peer.payment.okpay.BlockingFundsActor._

class OkPayProcessorActor(account: AccountId, client: OkPayClient, pollingInterval: FiniteDuration)
  extends Actor {

  import OkPayProcessorActor._
  import context.dispatcher

  override def receive: Receive = {
    case Initialize(eventChannel) =>
      new InitializedBehavior(eventChannel).start()
  }

  private var timer: Cancellable = _

  override def preStart(): Unit = {
    timer = context.system.scheduler.schedule(
      initialDelay = pollingInterval,
      interval = pollingInterval,
      receiver = self,
      message = PollBalance
    )
  }

  override def postStop(): Unit = {
    Option(timer).foreach(_.cancel())
  }

  private class InitializedBehavior(eventChannel: ActorRef) extends EventProducer(eventChannel) {

    private val blockingFunds = context.actorOf(BlockingFundsActor.props, "blocking")

    def start(): Unit = {
      pollBalances()
      context.become(managePayments)
    }

    val managePayments: Receive = {
      case PaymentProcessorActor.Identify =>
        sender ! Identified(OkPayProcessorActor.Id)
      case pay: Pay[_] =>
        sendPayment(sender(), pay)
      case FindPayment(paymentId) =>
        findPayment(sender(), paymentId)
      case RetrieveBalance(currency) =>
        currentBalance(sender(), currency)
      case PollBalance =>
        pollBalances()
      case msg @ (BlockFunds(_, _) | UnblockFunds(_)) =>
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

    private def useFunds[C <: FiatCurrency](pay: Pay[C]): Future[Unit] =
      AskPattern(blockingFunds, UseFunds(pay.fundsId, pay.amount), "fail to use funds")
        .withImmediateReply[Any]()
        .flatMap {
          case FundsUsed(pay.`fundsId`, pay.`amount`) =>
            Future.successful()
          case CannotUseFunds(pay.`fundsId`, pay.`amount`, cause) =>
            Future.failed(new RuntimeException(cause))
        }

    private def findPayment(requester: ActorRef, paymentId: PaymentId): Unit = {
      client.findPayment(paymentId).onComplete {
        case Success(Some(payment)) => requester ! PaymentFound(payment)
        case Success(None) => requester ! PaymentNotFound(paymentId)
        case Failure(error) => requester ! FindPaymentFailed(paymentId, error)
      }
    }

    private def currentBalance[C <: FiatCurrency](requester: ActorRef, currency: C): Unit = {
      val query = for {
        balance <- client.currentBalance(currency)
        blocking <- blockedFundsForCurrency(currency)
      } yield (balance, blocking)

      query.onComplete {
        case Success((balance, blocking)) =>
          updateBalances(Seq(balance))
          requester ! BalanceRetrieved(balance, blocking)
        case Failure(error) =>
          requester ! BalanceRetrievalFailed(currency, error)
      }
    }

    private def blockedFundsForCurrency[C <: FiatCurrency](currency: C): Future[CurrencyAmount[C]] = {
      AskPattern(blockingFunds, RetrieveBlockedFunds(currency))
        .withImmediateReply[BlockedFunds[C]]().map(_.funds)
    }

    private def updateBalances(balances: Seq[FiatAmount]): Unit = {
      for (balance <- balances) {
        produceEvent(FiatBalanceChangeEvent(balance))
      }
      blockingFunds ! BalancesUpdate(balances)
    }

    private def pollBalances(): Unit = {
      client.currentBalances().onSuccess { case balances => updateBalances(balances) }
    }

    private def balanceNotFound(currency: FiatCurrency): RetrieveBalanceResponse =
      BalanceRetrievalFailed(currency, new NoSuchElementException("No balance in that currency"))


  }
}

object OkPayProcessorActor {

  val Id = "OKPAY"

  private case object PollBalance

  def props(config: Config) = {
    val account = config.getString("coinffeine.okpay.id")
    val endpoint = URI.create(config.getString("coinffeine.okpay.endpoint"))
    val client = new OkPayWebServiceClient(
      account = config.getString("coinffeine.okpay.id"),
      seedToken = config.getString("coinffeine.okpay.token"),
      baseAddressOverride = Some(endpoint)
    )
    val pollingInterval =
      config.getDuration("coinffeine.okpay.pollingInterval", TimeUnit.MILLISECONDS).millis
    Props(new OkPayProcessorActor(account, client, pollingInterval))
  }
}
