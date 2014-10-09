package coinffeine.peer.market.orders.funds

import scala.util.{Failure, Success, Try}

import akka.actor._

import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.{Exchange, ExchangeId}
import coinffeine.model.market.RequiredFunds
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

/** Manages funds blocking for an exchange */
private[funds] class FundsBlockerActor(
    id: ExchangeId,
    wallet: ActorRef,
    paymentProcessor: ActorRef,
    requiredFunds: RequiredFunds[_ <: FiatCurrency],
    listener: ActorRef) extends Actor with ActorLogging {

  type BlockedBitcoin = BlockedCoinsId

  private sealed trait FiatFunds {
    def unblock(): Unit = {}
    def result: Option[Try[Unit]] = None
  }
  private case object NoFiatFunds extends FiatFunds {
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
  }
  private case object UnneededFiatFunds extends FiatFunds {
    override val result = Some(Success {})
  }
  private case class BlockedFiatFunds(available: Boolean) extends FiatFunds {
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
    override val result = Some(
      if (available) Success {}
      else Failure(new Error(s"${requiredFunds.fiat} blocked for $id but not available"))
    )
  }

  private sealed trait BitcoinFunds {
    def unblock(): Unit = {}
    def result: Option[Try[BlockedBitcoin]] = None
  }
  private case object NoBitcoinFunds extends BitcoinFunds
  private case class SuccessfullyBlockedBitcoins(funds: BlockedBitcoin) extends BitcoinFunds {
    override val result = Some(Success(funds))
    override def unblock(): Unit = {
      wallet ! WalletActor.UnblockBitcoins(funds)
    }
  }
  private case object FailedBitcoinBlocking extends BitcoinFunds {
    override val result = Some(Failure(new Error(s"Cannot block ${requiredFunds.bitcoin}")))
  }

  private var fiatFunds: FiatFunds = NoFiatFunds
  private var bitcoinFunds: BitcoinFunds = NoBitcoinFunds

  override def preStart(): Unit = {
    requestFiatFunds()
    requestBitcoinFunds()
  }

  private def requestFiatFunds(): Unit = {
    if (isFiatRequired) {
      paymentProcessor ! PaymentProcessorActor.BlockFunds(id, requiredFunds.fiat)
    } else {
      fiatFunds = UnneededFiatFunds
    }
  }

  private def isFiatRequired: Boolean = requiredFunds.fiat.isPositive

  private def requestBitcoinFunds(): Unit = {
    wallet ! WalletActor.BlockBitcoins(requiredFunds.bitcoin)
  }

  override def receive: Receive = receiveResponses.andThen(_ => tryToCompleteFundsBlocking())

  private def receiveResponses: Receive = {
    case WalletActor.BlockedBitcoins(funds) =>
      bitcoinFunds = SuccessfullyBlockedBitcoins(funds)

    case WalletActor.CannotBlockBitcoins =>
      bitcoinFunds = FailedBitcoinBlocking

    case PaymentProcessorActor.AvailableFunds(`id`) =>
      fiatFunds = BlockedFiatFunds(available = true)

    case PaymentProcessorActor.UnavailableFunds(`id`) =>
      fiatFunds = BlockedFiatFunds(available = false)
  }

  private def tryToCompleteFundsBlocking(): Unit = {
    (fiatFunds.result, bitcoinFunds.result) match {
      case (Some(fiatResult), Some(bitcoinResult)) =>
        completeFundsBlocking(fiatResult, bitcoinResult)
      case _ => // Not yet ready
    }
  }

  private def completeFundsBlocking(fiatResult: Try[Unit],
                                    bitcoinResult: Try[BlockedBitcoin]): Unit = {
    val overallResult = for {
      fiatFunds <- fiatResult
      bitcoinFunds <- bitcoinResult
    } yield Exchange.BlockedFunds(if (isFiatRequired) Some(id) else None, bitcoinFunds)

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
  case class BlockingResult(maybeFunds: Try[Exchange.BlockedFunds])
}
