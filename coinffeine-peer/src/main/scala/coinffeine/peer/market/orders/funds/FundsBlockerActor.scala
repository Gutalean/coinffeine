package coinffeine.peer.market.orders.funds

import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

import akka.actor._

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

/** Manages funds blocking for an exchange */
private[funds] class FundsBlockerActor(
    id: ExchangeId,
    wallet: ActorRef,
    paymentProcessor: ActorRef,
    requiredFunds: RequiredFunds[_ <: FiatCurrency],
    listener: ActorRef) extends Actor with ActorLogging {

  private trait Funds {
    def unblock(): Unit = {}
    def finished: Boolean = false
    def result: Try[Unit] = Failure(new IllegalStateException("Not ready") with NoStackTrace)
  }

  private sealed trait FiatFunds extends Funds
  private case object NoFiatFunds extends FiatFunds {
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
  }
  private case object UnneededFiatFunds extends FiatFunds {
    override val finished = true
    override val result = Success {}
  }
  private case class BlockedFiatFunds(available: Boolean) extends FiatFunds {
    override val finished = true
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
    override val result =
      if (available) Success {}
      else Failure(new Error(s"${requiredFunds.fiat} blocked for $id but not available"))
  }

  private sealed trait BitcoinFunds extends Funds
  private case object NoBitcoinFunds extends BitcoinFunds
  private case object SuccessfullyBlockedBitcoins extends BitcoinFunds {
    override val finished = true
    override val result = Success {}
    override def unblock(): Unit = {
      wallet ! WalletActor.UnblockBitcoins(id)
    }
  }
  private case class FailedBitcoinBlocking(reason: String) extends BitcoinFunds {
    override val finished = true
    override val result = Failure(new Error(s"Cannot block ${requiredFunds.bitcoin}: $reason"))
  }

  private var fiatFunds: FiatFunds = NoFiatFunds
  private var bitcoinFunds: BitcoinFunds = NoBitcoinFunds

  override def preStart(): Unit = {
    requestFiatFunds()
    requestBitcoinFunds()
  }

  private def requestFiatFunds(): Unit = {
    if (isFiatRequired) {
      context.system.eventStream.subscribe(self,
        classOf[PaymentProcessorActor.FundsAvailabilityEvent])
      paymentProcessor ! PaymentProcessorActor.BlockFunds(id, requiredFunds.fiat)
    } else {
      fiatFunds = UnneededFiatFunds
    }
  }

  private def isFiatRequired: Boolean = requiredFunds.fiat.isPositive

  private def requestBitcoinFunds(): Unit = {
    wallet ! WalletActor.BlockBitcoins(id, requiredFunds.bitcoin)
  }

  override def receive: Receive = receiveResponses.andThen(_ => tryToCompleteFundsBlocking())

  private def receiveResponses: Receive = {
    case WalletActor.BlockedBitcoins(funds) =>
      bitcoinFunds = SuccessfullyBlockedBitcoins

    case WalletActor.CannotBlockBitcoins(reason) =>
      bitcoinFunds = FailedBitcoinBlocking(reason)

    case PaymentProcessorActor.AvailableFunds(`id`) =>
      fiatFunds = BlockedFiatFunds(available = true)

    case PaymentProcessorActor.UnavailableFunds(`id`) =>
      fiatFunds = BlockedFiatFunds(available = false)
  }

  private def tryToCompleteFundsBlocking(): Unit = {
    if (fiatFunds.finished && bitcoinFunds.finished) {
      completeFundsBlocking()
    }
  }

  private def completeFundsBlocking(): Unit = {
    val overallResult = for {
      _ <- fiatFunds.result
      _ <- bitcoinFunds.result
    } yield ()

    if (overallResult.isFailure) {
      bitcoinFunds.unblock()
      fiatFunds.unblock()
    }

    listener ! FundsBlockerActor.BlockingResult(overallResult)
    context.stop(self)
  }
}

object FundsBlockerActor {

  def props(id: ExchangeId,
            wallet: ActorRef,
            paymentProcessor: ActorRef,
            requiredFunds: RequiredFunds[_ <: FiatCurrency],
            listener: ActorRef) =
    Props(new FundsBlockerActor(id, wallet, paymentProcessor, requiredFunds, listener))

  /** Message sent to the listener when blocking has finished either successfully or with failure */
  case class BlockingResult(maybeFunds: Try[Unit])
}
