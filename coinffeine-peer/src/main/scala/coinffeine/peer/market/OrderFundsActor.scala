package coinffeine.peer.market

import akka.actor.{Actor, ActorLogging, ActorRef}

import coinffeine.model.bitcoin.BlockedCoinsId
import coinffeine.model.currency.{BitcoinAmount, FiatAmount}
import coinffeine.model.exchange.Exchange.BlockedFunds
import coinffeine.model.payment.PaymentProcessor.BlockedFundsId
import coinffeine.peer.bitcoin.WalletActor
import coinffeine.peer.payment.PaymentProcessorActor

/** Manages funds blocking for an order */
class OrderFundsActor(wallet: ActorRef, paymentProcessor: ActorRef) extends Actor with ActorLogging {
  import coinffeine.peer.market.OrderFundsActor._

  private sealed trait FiatFunds {
    def available: Boolean
    def unblock(): Unit = {}
    def maybeId: Option[BlockedFundsId] = None
  }
  private case object NoFiatFunds extends FiatFunds {
    override val available = false
  }
  private case object UnneededFiatFunds extends FiatFunds {
    override val available = true
  }
  private case class BlockedFiatFunds(id: BlockedFundsId, available: Boolean) extends FiatFunds {
    override val maybeId = Some(id)
    override def unblock(): Unit = {
      paymentProcessor ! PaymentProcessorActor.UnblockFunds(id)
    }
  }

  override def receive: Receive = {
    case BlockFunds(fiatAmount, bitcoinAmount) =>
      new InitializedBehavior(fiatAmount, bitcoinAmount, sender()).start()
  }

  private class InitializedBehavior(
      fiatAmount: FiatAmount, bitcoinAmount: BitcoinAmount, listener: ActorRef) {

    private var fiatFunds: FiatFunds = NoFiatFunds
    private var btcFunds: Option[BlockedCoinsId] = None
    private var lastNotification: Option[Any] = None

    def start(): Unit = {
      if (fiatAmount.isPositive) {
        paymentProcessor ! PaymentProcessorActor.BlockFunds(fiatAmount, self)
      } else {
        fiatFunds = UnneededFiatFunds
      }
      wallet ! WalletActor.BlockBitcoins(bitcoinAmount)
      context.become(managingFunds andThen (_ => notifyListener()))
    }

    private val managingFunds: Receive = {
      case id: BlockedFundsId =>
        fiatFunds = BlockedFiatFunds(id, available = false)

      case WalletActor.BlockedBitcoins(id) =>
        btcFunds = Some(id)

      case WalletActor.CannotBlockBitcoins =>
        wallet ! WalletActor.SubscribeToWalletChanges

      case WalletActor.WalletChanged =>
        wallet ! (if (btcFunds.isEmpty) WalletActor.BlockBitcoins(bitcoinAmount)
                  else WalletActor.UnsubscribeToWalletChanges)

      case PaymentProcessorActor.AvailableFunds(id) =>
        fiatFunds match {
          case f@BlockedFiatFunds(`id`, _) => fiatFunds = f.copy(available = true)
          case _ => // do nothing
        }

      case PaymentProcessorActor.UnavailableFunds(id) =>
        fiatFunds match {
          case f@BlockedFiatFunds(`id`, _) => fiatFunds = f.copy(available = false)
          case _ => // do nothing
        }

      case UnblockFunds =>
        btcFunds.foreach { id => wallet ! WalletActor.UnblockBitcoins(id) }
        fiatFunds.unblock()
    }

    private def notifyListener(): Unit = {
      val notification = currentNotification
      if (lastNotification != Some(notification)) {
        listener ! notification
        lastNotification = Some(notification)
      }
    }

    def currentNotification = if (fiatFunds.available && btcFunds.isDefined) {
      AvailableFunds(BlockedFunds(fiatFunds.maybeId, btcFunds.get))
    } else UnavailableFunds
  }
}

object OrderFundsActor {
  /** Sent to the [[OrderFundsActor]] to initialize it. */
  case class BlockFunds(fiatAmount: FiatAmount, bitcoinAmount: BitcoinAmount)

  /** Whoever sent the [[BlockFunds]] message will receive this message when funds became
    * available. */
  case class AvailableFunds(blockedFunds: BlockedFunds)

  /** Whoever sent the [[BlockFunds]] message will receive this message when funds became
    * available. */
  case object UnavailableFunds

  /** Sent to the [[OrderFundsActor]] to release blocked funds and terminate himself */
  case object UnblockFunds
}
