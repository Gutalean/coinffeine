package coinffeine.peer.bitcoin.blockchain

import java.util
import scala.collection.JavaConversions._

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core.AbstractBlockChain.NewBlockType
import org.bitcoinj.core._

import coinffeine.model.bitcoin.Hash

private[blockchain] class BlockchainNotifier(blockChain: AbstractBlockChain)
  extends AbstractBlockChainListener with StrictLogging {
  import BlockchainNotifier._

  /** Subscription to the confirmation of a given transaction.
    *
    * @param txHash                 Hash of the transaction to observe
    * @param listener               Who to notify
    * @param requiredConfirmations  How deep the transaction should be
    * @param foundAtHeights         Blocks in which the transaction has been included in the
    *                               blockchain (if ever)
    */
  private case class ConfirmationSubscription(
      txHash: Hash,
      listener: ConfirmationListener,
      requiredConfirmations: Int,
      foundAtHeights: Map[Hash, Int] = Map.empty)

  private case class HeightSubscription(height: Long, listener: HeightListener)

  private var confirmationSubscriptions: Map[Sha256Hash, ConfirmationSubscription] = Map.empty
  private var heightSubscriptions: Set[HeightSubscription] = Set.empty
  private var currentHeight = blockChain.getBestChainHeight

  def watchTransactionConfirmation(tx: Hash,
                                   confirmations: Int,
                                   listener: ConfirmationListener): Unit = synchronized {
    logger.debug(s"Subscribing to $tx, waiting for $confirmations confirmations")
    confirmationSubscriptions += tx -> ConfirmationSubscription(tx, listener, confirmations)
  }

  def watchHeight(height: Long, listener: HeightListener): Unit = synchronized {
    if (height <= currentHeight) {
      logger.debug(s"Already at $currentHeight height no need to watch for $height")
      listener.heightReached(currentHeight)
    } else {
      logger.debug(s"Watching height until block $height")
      heightSubscriptions += HeightSubscription(height, listener)
    }
  }

  override def notifyNewBestBlock(block: StoredBlock): Unit = synchronized {
    currentHeight = block.getHeight
    logger.trace(s"New block ${block.getHeader.getHash} at height $currentHeight arrived")
    notifyConfirmedTransactions()
    notifyHeight()
  }

  override def reorganize(splitPoint: StoredBlock, oldBlocks: util.List[StoredBlock],
                          newBlocks: util.List[StoredBlock]): Unit = synchronized {
    logger.warn(s"Blockchain reorganization from height ${splitPoint.getHeight}")

    /* It seems to be a bug in Bitcoinj that causes the newBlocks list to be in an arbitrary
     * order although the Javadoc of BlockChainListener says it follows a top-first order.
     * Thus, we have to sort the blocks from the list to determine the correct order.
     */
    newBlocks.sortBy(_.getHeight).foreach(notifyNewBestBlock)
  }

  override def isTransactionRelevant(tx: Transaction): Boolean = synchronized {
    confirmationSubscriptions.contains(tx.getHash)
  }

  override def receiveFromBlock(
      tx: Transaction,
      block: StoredBlock,
      blockType: NewBlockType,
      relativityOffset: Int): Unit = {
    synchronized {
      updateConfirmationSubscriptions(tx.getHash, block)
    }
  }

  private def updateConfirmationSubscriptions(tx: Hash, block: StoredBlock): Unit = {
    logger.debug(s"tx $tx found in block ${block.getHeight}")
    confirmationSubscriptions.get(tx).foreach { subscription =>
      logger.info(s"tx $tx found in block ${block.getHeight}: " +
        s"waiting for ${subscription.requiredConfirmations} confirmations")
      confirmationSubscriptions += tx -> subscription.copy(
        foundAtHeights = subscription.foundAtHeights +
          (block.getHeader.getHash -> block.getHeight))
    }
  }

  override def notifyTransactionIsInBlock(txHash: Sha256Hash,
                                          block: StoredBlock,
                                          blockType: NewBlockType,
                                          relativityOffset: Int): Boolean = {
    logger.trace(s"Transaction $txHash in block ${block.getHeader.getHashAsString} " +
      s"at height ${block.getHeight}")
    updateConfirmationSubscriptions(txHash, block)
    confirmationSubscriptions.contains(txHash)
  }

  private def notifyConfirmedTransactions(): Unit = {
    confirmationSubscriptions.foreach {
      case (txHash, ConfirmationSubscription(_, listener, reqConf, foundAtHeights)) =>
        val confirmations = mainChainConfirmations(foundAtHeights).getOrElse(0)
        val event =
          s"""after new chain head $currentHeight, tx $txHash have $confirmations
             |confirmations out of $reqConf required""".stripMargin
        if (confirmations >= reqConf) {
          logger.info("{}: reporting to the observer", event)
          confirmationSubscriptions -= txHash
          listener.confirmed(txHash, confirmations)
        } else {
          logger.info("{}: still waiting for more blocks", event)
        }

      case (_, subscription) => logger.trace(s"Still waiting for $subscription")
    }
  }

  private def mainChainConfirmations(foundAtHeights: Map[Hash, Int]): Option[Int] =
    foundAtHeights.collectFirst {
      case (blockHash, seenAtHeight) if !blockChain.isOrphan(blockHash) =>
        currentHeight - seenAtHeight + 1
    }


  private def notifyHeight(): Unit = {
    heightSubscriptions.foreach { case subscription @ HeightSubscription(height, listener) =>
      if (currentHeight >= height) {
        logger.info(s"Watched height $height was reached")
        heightSubscriptions -= subscription
        listener.heightReached(currentHeight)
      }
    }
  }
}

private[blockchain] object BlockchainNotifier {
  trait ConfirmationListener {
    def confirmed(tx: Hash, confirmations: Int): Unit
  }

  trait HeightListener {
    def heightReached(currentHeight: Long): Unit
  }
}
