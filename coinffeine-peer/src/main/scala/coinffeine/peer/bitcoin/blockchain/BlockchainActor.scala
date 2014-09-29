package coinffeine.peer.bitcoin.blockchain

import scala.collection.JavaConversions._

import akka.actor._
import com.google.bitcoin.core._
import com.google.bitcoin.script.ScriptBuilder

import coinffeine.model.bitcoin._

private class BlockchainActor(blockchain: AbstractBlockChain, network: NetworkParameters)
  extends Actor with ActorLogging {

  import BlockchainActor._

  private val wallet = new Wallet(network)
  private val notifier = new BlockchainNotifier

  override def preStart(): Unit = {
    blockchain.addListener(notifier)
    blockchain.addWallet(wallet)
  }

  override val receive: Receive = {

    case WatchPublicKey(key) =>
      wallet.addKey(key)

    case WatchMultisigKeys(keys) =>
      wallet.addWatchedScripts(Seq(ScriptBuilder.createMultiSigOutputScript(keys.size, keys)))

    case req @ WatchTransactionConfirmation(txHash, confirmations) =>
      notifier.watchTransactionConfirmation(txHash, confirmations, new ConfirmationListener(sender()))

    case RetrieveTransaction(txHash) =>
      sender ! (transactionFor(txHash) match {
        case Some(tx) => TransactionFound(txHash, ImmutableTransaction(tx))
        case None => TransactionNotFound(txHash)
      })

    case RetrieveBlockchainHeight =>
      sender() ! BlockchainHeightReached(blockchain.getBestChainHeight)

    case WatchBlockchainHeight(height) =>
      notifier.watchHeight(height, new HeightListener(sender()))

    case WatchOutput(output) =>
      notifier.watchOutput(output, new OutputListener(sender()))
  }

  private def transactionFor(txHash: Sha256Hash): Option[MutableTransaction] =
    Option(wallet.getTransaction(txHash))

  private class ConfirmationListener(requester: ActorRef)
    extends BlockchainNotifier.ConfirmationListener {

    override def confirmed(tx: Hash, confirmations: Int): Unit = {
      requester ! TransactionConfirmed(tx, confirmations)
    }

    override def rejected(tx: Hash): Unit = {
      requester ! TransactionRejected(tx)
    }
  }

  private class HeightListener(requester: ActorRef) extends BlockchainNotifier.HeightListener {
    override def heightReached(currentHeight: Long): Unit = {
      requester ! BlockchainHeightReached(currentHeight)
    }
  }

  private class OutputListener(requester: ActorRef) extends BlockchainNotifier.OutputListener {
    override def outputSpent(output: TransactionOutPoint, tx: ImmutableTransaction): Unit = {
      requester ! OutputSpent(output, tx)
    }
  }
}

/** A BlockchainActor keeps a blockchain and can:
  *
  * - Notify when a transaction reaches a number of confirmations.
  * - Return the transaction associated with a hash
  */
object BlockchainActor {

  private[bitcoin] def props(blockchain: AbstractBlockChain, network: NetworkParameters): Props =
    Props(new BlockchainActor(blockchain, network))

  /** A message sent to the blockchain actor requesting to watch for transactions on the given
    * public key.
    */
  case class WatchPublicKey(publicKey: PublicKey)

  /** A message sent to the blockchain actor requesting to watch for transactions multisigned
    * for this combination of keys.
    */
  case class WatchMultisigKeys(keys: Seq[PublicKey])

  /** A message sent to the blockchain actor requesting to watch for confirmation of the
    * given block.
    *
    * The blockchain actor will send either `TransactionConfirmed` or `TransactionRejected`
    * as response.
    */
  case class WatchTransactionConfirmation(transactionHash: Hash, confirmations: Int)

  /** A message sent by the blockchain actor to notify that the transaction has reached the
    * requested number of confirmations. */
  case class TransactionConfirmed(transactionHash: Hash, confirmations: Int)

  /** A message sent by the blockchain actor to notify that the transaction has been rejected. */
  case class TransactionRejected(transactionHash: Hash)

  /** A message sent to the blockchain actor requesting the current chain height. To be replied
    * with a [[BlockchainHeightReached]]. */
  case object RetrieveBlockchainHeight

  /** A message sent to the blockchain actor requesting to be notified when the best block in the
    * blockchain reaches a specified height.
    */
  case class WatchBlockchainHeight(height: Long)

  /** A message sent by the blockchain actor to notify that the blockchain has reached a certain
    * height.
    */
  case class BlockchainHeightReached(height: Long)

  /** Listen for an output to be notified with an [[OutputSpent]] whenever the output get spent. */
  case class WatchOutput(output: TransactionOutPoint)

  /** An output was spent by the given transaction */
  case class OutputSpent(output: TransactionOutPoint, tx: ImmutableTransaction)

  /** A message sent to the blockchain actor to retrieve a transaction from its hash.
    *
    * The blockchain actor will send either a `TransactionFound` or `TransactionNotFound`
    * as response.
    */
  case class RetrieveTransaction(hash: Hash)

  /** A message sent by the blockchain actor to indicate a transaction was found in the blockchain
    * for the given hash.
    */
  case class TransactionFound(hash: Hash, tx: ImmutableTransaction)

  /** A message sent by the blockchain actor to indicate a transaction was not found in the
    * blockchain for the given hash.
    */
  case class TransactionNotFound(hash: Hash)
}
