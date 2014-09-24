package coinffeine.peer.bitcoin

import java.util
import scala.collection.JavaConversions._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core.AbstractBlockChain.NewBlockType
import com.google.bitcoin.core._
import com.google.bitcoin.script.ScriptBuilder

import coinffeine.model.bitcoin._

private class BlockchainActor(blockchain: AbstractBlockChain, network: NetworkParameters)
  extends Actor with ActorLogging {

  import BlockchainActor._

  private var confirmationSubscriptions: Map[Sha256Hash, ConfirmationSubscription] = Map.empty
  private var heightSubscriptions: Set[HeightSubscription] = Set.empty
  private val wallet = new Wallet(network)

  override def preStart(): Unit = {
    blockchain.addListener(Listener, context.dispatcher)
    blockchain.addWallet(wallet)
  }

  override val receive: Receive = {

    case WatchPublicKey(key) =>
      wallet.addKey(key)

    case WatchMultisigKeys(keys) =>
      wallet.addWatchedScripts(Seq(ScriptBuilder.createMultiSigOutputScript(keys.size, keys)))

    case req @ WatchTransactionConfirmation(txHash, confirmations) =>
      confirmationSubscriptions += txHash -> ConfirmationSubscription(txHash, sender(), confirmations)

    case RetrieveTransaction(txHash) =>
      sender ! (transactionFor(txHash) match {
        case Some(tx) => TransactionFound(txHash, ImmutableTransaction(tx))
        case None => TransactionNotFound(txHash)
      })

    case RetrieveBlockchainHeight =>
      sender() ! BlockchainHeightReached(blockchain.getBestChainHeight)

    case WatchBlockchainHeight(height) =>
      heightSubscriptions += HeightSubscription(height, sender())
  }

  private object Listener extends AbstractBlockChainListener {

    override def notifyNewBestBlock(block: StoredBlock): Unit = {
      val currentHeight = block.getHeight
      notifyConfirmedTransactions(currentHeight)
      notifyHeight(currentHeight)
    }

    override def reorganize(splitPoint: StoredBlock, oldBlocks: util.List[StoredBlock],
                            newBlocks: util.List[StoredBlock]): Unit = {
      val seenTxs = confirmationSubscriptions.values.filter(_.foundInBlock.isDefined)
      val rejectedTransactions = seenTxs.filterNot(tx =>
        newBlocks.toSeq.exists(block => block.getHeader.getHash == tx.foundInBlock.get.hash))
      rejectedTransactions.foreach { subscription =>
        log.info("tx {} is lost in blockchain reorganization; reporting to the observer",
          subscription.txHash)
        subscription.requester ! TransactionRejected(subscription.txHash)
        confirmationSubscriptions -= subscription.txHash
      }
      /* It seems to be a bug in Bitcoinj that causes the newBlocks list to be in an arbitrary
       * order although the Javadoc of BlockChainListener says it follows a top-first order.
       * Thus, we have to sort the blocks from the list to determine the correct order.
       */
      newBlocks.sortBy(_.getHeight).foreach(notifyNewBestBlock)
    }

    override def isTransactionRelevant(tx: Transaction): Boolean =
      confirmationSubscriptions.contains(tx.getHash)

    override def receiveFromBlock(tx: Transaction, block: StoredBlock,
                                  blockType: NewBlockType, relativityOffset: Int): Unit = {
      val txHash = tx.getHash
      val txHeight= block.getHeight
      confirmationSubscriptions.get(txHash) match {
        case Some(subscription) =>
          log.info("tx {} found in block {}: waiting for {} confirmations",
            txHash, txHeight, subscription.requiredConfirmations)
          confirmationSubscriptions += txHash -> subscription.copy(
            foundInBlock = Some(BlockIdentity(block.getHeader.getHash, block.getHeight)))
        case None =>
          log.warning("tx {} received but not relevant (not being observed)", txHash)
      }
    }

    override def notifyTransactionIsInBlock(
        txHash: Sha256Hash, block: StoredBlock,
        blockType: NewBlockType, relativityOffset: Int): Boolean =
      confirmationSubscriptions.contains(txHash)

    private def notifyConfirmedTransactions(currentHeight: Int): Unit = {
      confirmationSubscriptions.foreach {
        case (txHash, ConfirmationSubscription(_, req, reqConf, Some(foundInBlock))) =>
          val confirmations = (currentHeight - foundInBlock.height) + 1
          if (confirmations >= reqConf) {
            log.info(
              """after new chain head {}, tx {} have {} confirmations out of {} required:
                |reporting to the observer""".stripMargin,
              currentHeight, txHash, confirmations, reqConf)
            req ! TransactionConfirmed(txHash, confirmations)
            confirmationSubscriptions -= txHash
          } else {
            log.info(
              """after new chain head {}, tx {} have {} confirmations out of {} required:
                |still waiting for more blocks""".stripMargin,
              currentHeight, txHash, confirmations, reqConf)
          }
        case _ =>
      }
    }

    private def notifyHeight(currentHeight: Int): Unit = {
      heightSubscriptions.foreach { case subscription @ HeightSubscription(height, req) =>
        if (currentHeight >= height) {
          req ! BlockchainHeightReached(currentHeight)
          heightSubscriptions -= subscription
        }
      }
    }
  }

  private def transactionFor(txHash: Sha256Hash): Option[MutableTransaction] =
    Option(wallet.getTransaction(txHash))
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

  private case class BlockIdentity(hash: Sha256Hash, height: Int)

  /** Subscription to the confirmation of a given transaction.
    *
    * @param txHash                 Hash of the transaction to observe
    * @param requester              Who to notify
    * @param requiredConfirmations  How deep the transaction should be
    * @param foundInBlock           Block in which the transaction is included on the blockchain
    *                               (if ever)
    */
  private case class ConfirmationSubscription(txHash: Sha256Hash,
                                              requester: ActorRef,
                                              requiredConfirmations: Int,
                                              foundInBlock: Option[BlockIdentity] = None)

  private case class HeightSubscription(height: Long, requester: ActorRef)
}
