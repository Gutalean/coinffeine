package coinffeine.peer.bitcoin

import scala.util.control.NonFatal

import akka.actor._
import com.google.bitcoin.core.AbstractWalletEventListener

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin._
import coinffeine.model.currency.BitcoinAmount
import coinffeine.peer.CoinffeinePeerActor.{RetrieveWalletBalance, WalletBalance}
import coinffeine.peer.api.event.WalletBalanceChangeEvent
import coinffeine.peer.event.EventProducer

private class WalletActor extends Actor with ActorLogging {

  import coinffeine.peer.bitcoin.WalletActor._

  override val receive: Receive = {
    case Initialize(wallet, eventChannel) =>
      new InitializedWalletActor(wallet, eventChannel).start()
  }

  private class InitializedWalletActor(wallet: Wallet, channel: ActorRef)
    extends EventProducer(channel) {

    def start(): Unit = {
      subscribeToWalletChanges()
      produceEvent(WalletBalanceChangeEvent(wallet.balance()))
      context.become(manageWallet)
    }

    private val manageWallet: Receive = {

      case req @ WalletActor.BlockFundsInMultisign(signatures, amount) =>
        try {
          val tx = wallet.blockMultisignFunds(signatures, amount)
          sender ! WalletActor.FundsBlocked(req, tx)
        } catch {
          case NonFatal(ex) => sender ! WalletActor.FundsBlockingError(req, ex)
        }

      case WalletActor.ReleaseFunds(tx) =>
        wallet.releaseFunds(tx)

      case RetrieveWalletBalance =>
        sender() ! WalletBalance(wallet.balance())

      case WalletChanged =>
        produceEvent(WalletBalanceChangeEvent(wallet.balance()))
    }

    private def subscribeToWalletChanges(): Unit = {
      wallet.addEventListener(new AbstractWalletEventListener {
        override def onChange(): Unit = {
          self ! WalletChanged
        }
      }, context.dispatcher)
    }
  }
}

object WalletActor {

  private case object WalletChanged

  private[bitcoin] val props = Props(new WalletActor)
  private[bitcoin] case class Initialize(wallet: Wallet, eventChannel: ActorRef)

  /** A message sent to the wallet actor in order to block some funds in a multisign transaction.
    *
    * This message requests some funds to be blocked by the wallet. This will produce a new
    * transaction included in a FundsBlocked reply message, or FundsBlockingError if something
    * goes wrong. The resulting transaction can be safely sent to the blockchain with the guarantee
    * that the outputs it spends are not used in any other transaction. If the transaction is not
    * finally broadcast to the blockchain, the funds can be unblocked by sending a ReleasedFunds
    * message.
    *
    * @param requiredSignatures The signatures required to spend the tx in a multisign script
    * @param amount             The amount of bitcoins to be blocked and included in the transaction
    */
  case class BlockFundsInMultisign(requiredSignatures: Seq[KeyPair], amount: BitcoinAmount)

  /** A message sent by the wallet actor in reply to a BlockFundsInMultisign message to report
    * a successful funds blocking.
    *
    * @param request  The request this message is replying to
    * @param tx       The resulting transaction that contains the funds that have been blocked
    */
  case class FundsBlocked(request: BlockFundsInMultisign, tx: MutableTransaction)

  /** A message sent by the wallet actor in reply to a BlockFundsInMultisign message to report
    * an error while blocking the funds.
    */
  case class FundsBlockingError(request: BlockFundsInMultisign, error: Throwable)

  /** A message sent to the wallet actor in order to release the funds that are blocked by the
    * given transaction.
    */
  case class ReleaseFunds(tx: MutableTransaction)
}
