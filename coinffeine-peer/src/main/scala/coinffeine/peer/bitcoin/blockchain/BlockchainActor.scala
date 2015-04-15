package coinffeine.peer.bitcoin.blockchain

import scala.collection.JavaConversions._

import akka.actor.{Address => _, _}
import org.bitcoinj.core._
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.Both
import coinffeine.model.bitcoin._

private class BlockchainActor(blockchain: AbstractBlockChain, wallet: Wallet)
  extends Actor with ActorLogging {

  import BlockchainActor._

  private val notifier = new BlockchainNotifier(blockchain.getBestChainHeight)

  override def preStart(): Unit = {
    blockchain.addListener(notifier)
  }

  override val receive: Receive = {

    case WatchPublicKey(key) =>
      wallet.importKey(key)

    case WatchMultisigKeys(keys) =>
      val script = ScriptBuilder.createMultiSigOutputScript(keys.toSeq.size, keys.toSeq)
      wallet.addWatchedScripts(Seq(script))

    case req @ WatchTransactionConfirmation(txHash, confirmations) =>
      val confirmation = new ConfirmationListener(sender())
      def watchForConfirmation() =
        notifier.watchTransactionConfirmation(txHash, confirmations, confirmation)
      def confirmImmediately(tx: MutableTransaction) =
        confirmation.confirmed(txHash, tx.getConfidence.getDepthInBlocks)
      transactionFor(txHash)
        .filter(_.getConfidence.getDepthInBlocks >= confirmations)
        .fold(watchForConfirmation())(confirmImmediately)

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
      val listener = new OutputListener(output, sender())
      findTransactionSpending(output.getOutPointFor)
        .fold(subscribeToOutput(output, listener)) { tx =>
          listener.outputSpent(ImmutableTransaction(tx))
        }
  }

  private def findTransactionSpending(outPoint: TransactionOutPoint): Option[MutableTransaction] =
    (for {
      tx <- wallet.getTransactions(false)
      input <- tx.getInputs
      if input.getOutpoint == outPoint
    } yield tx).headOption

  private def transactionFor(txHash: Sha256Hash): Option[MutableTransaction] =
    Option(wallet.getTransaction(txHash))

  private def subscribeToOutput(output: MutableTransactionOutput, listener: OutputListener) = {
    addressesOf(output).foreach(wallet.addWatchedAddress)
    notifier.watchOutput(output.getOutPointFor, listener)
  }

  private def addressesOf(output: MutableTransactionOutput): Seq[Address] = output match {
    case MultiSigOutput(info) => info.possibleKeys.map(_.toAddress(wallet.getParams))
    case _ => Seq(output.getScriptPubKey.getToAddress(wallet.getParams))
  }

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

  private class OutputListener(output: MutableTransactionOutput, requester: ActorRef)
    extends BlockchainNotifier.OutputListener {

    override def outputSpent(tx: ImmutableTransaction): Unit = {
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

  private[bitcoin] def props(blockchain: AbstractBlockChain, wallet: Wallet): Props =
    Props(new BlockchainActor(blockchain, wallet))

  /** A message sent to the blockchain actor requesting to watch for transactions on the given
    * public key.
    */
  case class WatchPublicKey(publicKey: PublicKey) {

    /** We should override equals as [[PublicKey]] is taking key creation time in consideration */
    override def equals(obj: scala.Any): Boolean = obj match {
      case WatchPublicKey(otherPublicKey) => PublicKey.areEqual(publicKey, otherPublicKey)
      case _ => false
    }
  }

  /** A message sent to the blockchain actor requesting to watch for transactions multisigned
    * for this combination of keys.
    */
  case class WatchMultisigKeys(keys: Both[PublicKey]) {

    /** We should override equals as [[PublicKey]] is taking key creation time in consideration */
    override def equals(obj: scala.Any): Boolean = obj match {
      case WatchMultisigKeys(otherKeys) =>
        keys.zip(otherKeys).forall((PublicKey.areEqual _).tupled)
      case _ => false
    }
  }

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
    * blockchain reaches a specified height. If, when receiving this request the chain is tallest
    * an immediate response with the current height will be sent in response.
    */
  case class WatchBlockchainHeight(height: Long)

  /** A message sent by the blockchain actor to notify that the blockchain has reached a certain
    * height.
    */
  case class BlockchainHeightReached(height: Long)

  /** Listen for an output to be notified with an [[OutputSpent]] whenever the output get spent. */
  case class WatchOutput(output: MutableTransactionOutput) {
    override def equals(other: Any): Boolean = other match {
      case WatchOutput(otherOutput) => output.getOutPointFor == otherOutput.getOutPointFor
      case _ => false
    }
    override def hashCode(): Int = output.getOutPointFor.hashCode()
  }

  /** An output was spent by the given transaction */
  case class OutputSpent(output: MutableTransactionOutput, tx: ImmutableTransaction) {

    override def equals(otherObject: Any): Boolean = otherObject match {
      case other: OutputSpent =>
        output.getOutPointFor == other.output.getOutPointFor && tx == other.tx
      case _ => false
    }

    override def hashCode(): Int = output.getOutPointFor.hashCode() * 13 + tx.hashCode()
  }

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
