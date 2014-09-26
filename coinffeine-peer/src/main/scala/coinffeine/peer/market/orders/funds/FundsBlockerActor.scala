package coinffeine.peer.market.orders.funds

import scala.util.{Failure, Success, Try}

import akka.actor._

import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Exchange
import coinffeine.model.market.RequiredFunds
import coinffeine.model.payment.PaymentProcessor
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

/** Manages funds blocking for an exchange */
private[funds] class FundsBlockerActor(
    wallet: ActorRef,
    paymentProcessor: ActorRef,
    requiredFunds: RequiredFunds[_ <: FiatCurrency],
    listener: ActorRef) extends Actor with ActorLogging {

  type BlockedFiat = Option[PaymentProcessor.BlockedFundsId]
  type BlockedBitcoin = BlockedCoinsId

  private sealed trait FiatFunds {
    def unblock(): Unit = {}
    def result: Option[Try[BlockedFiat]] = None
  }
  private case object NoFiatFunds extends FiatFunds
  private case object UnneededFiatFunds extends FiatFunds {
    override val result = Some(Success(None))
  }
  private case class UnknownAvailabilityFiatFunds(id: PaymentProcessor.BlockedFundsId)
    extends FiatFunds {
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
  }
  private case class BlockedFiatFunds(id: PaymentProcessor.BlockedFundsId, available: Boolean)
    extends FiatFunds {
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
    override val result = Some(
      if (available) Success(Some(id))
      else Failure(new Error(s"${requiredFunds.fiat} blocked in $id but not available"))
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
    if (requiredFunds.fiat.isPositive) {
      paymentProcessor ! PaymentProcessorActor.BlockFunds(requiredFunds.fiat)
    } else {
      fiatFunds = UnneededFiatFunds
    }
  }

  private def requestBitcoinFunds(): Unit = {
    wallet ! WalletActor.BlockBitcoins(requiredFunds.bitcoin)
  }

  override def receive: Receive = receiveResponses.andThen(_ => tryToCompleteFundsBlocking())

  private def receiveResponses: Receive = {
    case id: PaymentProcessor.BlockedFundsId =>
      fiatFunds = UnknownAvailabilityFiatFunds(id)

    case WalletActor.BlockedBitcoins(funds) =>
      bitcoinFunds = SuccessfullyBlockedBitcoins(funds)

    case WalletActor.CannotBlockBitcoins =>
      bitcoinFunds = FailedBitcoinBlocking

    case PaymentProcessorActor.AvailableFunds(id) =>
      fiatFunds match {
        case f: UnknownAvailabilityFiatFunds if f.id == id =>
          fiatFunds = BlockedFiatFunds(id, available = true)
        case _ => // do nothing
      }

    case PaymentProcessorActor.UnavailableFunds(id) =>
      fiatFunds match {
        case f: UnknownAvailabilityFiatFunds if f.id == id =>
          fiatFunds = BlockedFiatFunds(id, available = false)
        case _ => // do nothing
      }
  }

  private def tryToCompleteFundsBlocking(): Unit = {
    (fiatFunds.result, bitcoinFunds.result) match {
      case (Some(fiatResult), Some(bitcoinResult)) =>
        completeFundsBlocking(fiatResult, bitcoinResult)
      case _ => // Not yet ready
    }
  }

  private def completeFundsBlocking(fiatResult: Try[BlockedFiat],
                                    bitcoinResult: Try[BlockedBitcoin]): Unit = {
    val overallResult = for {
      fiatFunds <- fiatResult
      bitcoinFunds <- bitcoinResult
    } yield Exchange.BlockedFunds(fiatFunds, bitcoinFunds)

    if (overallResult.isFailure) {
      bitcoinFunds.unblock()
      fiatFunds.unblock()
    }

    listener ! FundsBlockerActor.BlockingResult(overallResult)
    context.stop(self)
  }
}

object FundsBlockerActor {

  def props(wallet: ActorRef,
            paymentProcessor: ActorRef,
            requiredFunds: RequiredFunds[_ <: FiatCurrency],
            listener: ActorRef) =
    Props(new FundsBlockerActor(wallet, paymentProcessor, requiredFunds, listener))

  /** Message sent to the listener when blocking has finished either successfully or with failure */
  case class BlockingResult(maybeFunds: Try[Exchange.BlockedFunds])
}
