package coinffeine.peer.bitcoin

import java.util
import scala.collection.JavaConversions._

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.bitcoin.core.AbstractBlockChain.NewBlockType
import com.google.bitcoin.core._

import coinffeine.model.bitcoin._

class BlockchainActor(network: NetworkParameters) extends Actor with ActorLogging {
  import BlockchainActor._

  override def receive = {
    case Initialize(blockchain) => new InitializedBlockchainActor(blockchain).start()
  }

  private class InitializedBlockchainActor(blockchain: AbstractBlockChain) {
    private var observations: Map[Sha256Hash, Observation] = Map.empty
    private var heightNotifications: Set[HeightNotification] = Set.empty
    private val wallet = new Wallet(network)

    def start(): Unit = {
      blockchain.addListener(Listener, context.dispatcher)
      blockchain.addWallet(wallet)
      context.become(handlingBlockchain)
    }

    private object Listener extends AbstractBlockChainListener {

      override def notifyNewBestBlock(block: StoredBlock): Unit = {
        val currentHeight = block.getHeight
        notifyConfirmedTransactions(currentHeight)
        notifyHeight(currentHeight)
      }

      override def reorganize(splitPoint: StoredBlock, oldBlocks: util.List[StoredBlock],
                              newBlocks: util.List[StoredBlock]): Unit = {
        val seenTxs = observations.values.filter(_.foundInBlock.isDefined)
        val rejectedObservations = seenTxs.filterNot(tx =>
          newBlocks.toSeq.exists(block => block.getHeader.getHash == tx.foundInBlock.get.hash))
        rejectedObservations.foreach { obs =>
          log.info("tx {} is lost in blockchain reorganization; reporting to the observer", obs.txHash)
          obs.requester ! TransactionRejected(obs.txHash)
          observations -= obs.txHash
        }
        /* It seems to be a bug in Bitcoinj that causes the newBlocks list to be in an arbitrary
         * order although the Javadoc of BlockChainListener says it follows a top-first order.
         * Thus, we have to calculate the highest block from the list to determine that's the
         * new blockchain head.
         */
        val newChainHead = newBlocks.maxBy(_.getHeight)
        notifyNewBestBlock(newChainHead)
      }

      override def isTransactionRelevant(tx: Transaction): Boolean = observations.contains(tx.getHash)

      override def receiveFromBlock(tx: Transaction, block: StoredBlock,
                                    blockType: NewBlockType, relativityOffset: Int): Unit = {
        val txHash = tx.getHash
        val txHeight= block.getHeight
        observations.get(txHash) match {
          case Some(obs) =>
            log.info("tx {} found in block {}: waiting for {} confirmations",
              txHash, txHeight, obs.requiredConfirmations)
            observations += txHash -> obs.copy(
              foundInBlock = Some(BlockIdentity(block.getHeader.getHash, block.getHeight)))
          case None =>
            log.warning("tx {} received but not relevant (not being observed)", txHash)
        }
      }

      override def notifyTransactionIsInBlock(
          txHash: Sha256Hash, block: StoredBlock,
          blockType: NewBlockType, relativityOffset: Int): Boolean = observations.contains(txHash)

      private def notifyConfirmedTransactions(currentHeight: Int): Unit = {
        observations.foreach {
          case (txHash, Observation(_, req, reqConf, Some(foundInBlock))) =>
            val confirmations = (currentHeight - foundInBlock.height) + 1
            if (confirmations >= reqConf) {
              log.info(
                """after new chain head {}, tx {} have {} confirmations out of {} required:
                  |reporting to the observer""".stripMargin,
                currentHeight, txHash, confirmations, reqConf)
              req ! TransactionConfirmed(txHash, confirmations)
              observations -= txHash
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
        heightNotifications.foreach { case notification@HeightNotification(height, req) =>
          if (currentHeight >= height) {
            req ! BlockchainHeightReached(currentHeight)
            heightNotifications -= notification
          }
        }
      }
    }

    private val handlingBlockchain: Receive = {

      case WatchPublicKey(key) =>
        wallet.addKey(key)

      case req @ WatchTransactionConfirmation(txHash, confirmations) =>
        observations += txHash -> Observation(txHash, sender(), confirmations)

      case RetrieveTransaction(txHash) =>
        transactionFor(txHash) match {
          case Some(tx) => sender ! TransactionFound(txHash, ImmutableTransaction(tx))
          case None => sender ! TransactionNotFound(txHash)
        }

      case RetrieveBlockchainHeight =>
        sender() ! BlockchainHeightReached(blockchain.getBestChainHeight)

      case WatchBlockchainHeight(height) =>
        heightNotifications += HeightNotification(height, sender())
    }

    private def transactionFor(txHash: Sha256Hash): Option[MutableTransaction] =
      Option(wallet.getTransaction(txHash))
  }
}

/** A BlockchainActor keeps a blockchain and can:
  *
  * - Notify when a transaction reaches a number of confirmations.
  * - Return the transaction associated with a hash
  */
object BlockchainActor {

  private[bitcoin] case class Initialize(blockchain: AbstractBlockChain)

  private[bitcoin] def props(network: NetworkParameters): Props =
    Props(new BlockchainActor(network))

  /** A message sent to the blockchain actor requesting to watch for transactions on the given
    * public key.
    */
  case class WatchPublicKey(publicKey: PublicKey)

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

  private case class Observation(txHash: Sha256Hash,
                                 requester: ActorRef,
                                 requiredConfirmations: Int,
                                 foundInBlock: Option[BlockIdentity] = None)

  private case class HeightNotification(height: Long, requester: ActorRef)
}
