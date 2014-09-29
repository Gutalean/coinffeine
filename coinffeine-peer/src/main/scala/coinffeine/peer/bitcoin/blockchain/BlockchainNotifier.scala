package coinffeine.peer.bitcoin.blockchain

import java.util
import scala.collection.JavaConversions._

import com.google.bitcoin.core.AbstractBlockChain.NewBlockType
import com.google.bitcoin.core._
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.Hash

private[blockchain] class BlockchainNotifier extends AbstractBlockChainListener {
  import BlockchainNotifier._

  private case class BlockIdentity(hash: Hash, height: Int)

  /** Subscription to the confirmation of a given transaction.
    *
    * @param txHash                 Hash of the transaction to observe
    * @param listener               Who to notify
    * @param requiredConfirmations  How deep the transaction should be
    * @param foundInBlock           Block in which the transaction is included on the blockchain
    *                               (if ever)
    */
  private case class ConfirmationSubscription(
      txHash: Hash,
      listener: ConfirmationListener,
      requiredConfirmations: Int,
      foundInBlock: Option[BlockIdentity] = None)

  private case class HeightSubscription(height: Long, listener: HeightListener)

  private var confirmationSubscriptions: Map[Sha256Hash, ConfirmationSubscription] = Map.empty
  private var heightSubscriptions: Set[HeightSubscription] = Set.empty

  def watchTransactionConfirmation(tx: Hash,
                                   confirmations: Int,
                                   listener: ConfirmationListener): Unit = {
    confirmationSubscriptions += tx -> ConfirmationSubscription(tx, listener, confirmations)
  }

  def watchHeight(height: Long, listener: HeightListener): Unit = {
    heightSubscriptions += HeightSubscription(height, listener)
  }

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
      Log.info("tx {} is lost in blockchain reorganization; reporting to the observer",
        subscription.txHash)
      confirmationSubscriptions -= subscription.txHash
      subscription.listener.rejected(subscription.txHash)
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
        Log.info("tx {} found in block {}: waiting for {} confirmations",
          Seq(txHash, txHeight, subscription.requiredConfirmations))
        confirmationSubscriptions += txHash -> subscription.copy(
          foundInBlock = Some(BlockIdentity(block.getHeader.getHash, block.getHeight)))
      case None =>
        Log.warn("tx {} received but not relevant (not being observed)", txHash)
    }
  }

  override def notifyTransactionIsInBlock(txHash: Sha256Hash, block: StoredBlock,
                                          blockType: NewBlockType, relativityOffset: Int): Boolean =
    confirmationSubscriptions.contains(txHash)

  private def notifyConfirmedTransactions(currentHeight: Int): Unit = {
    confirmationSubscriptions.foreach {
      case (txHash, ConfirmationSubscription(_, listener, reqConf, Some(foundInBlock))) =>
        val confirmations = (currentHeight - foundInBlock.height) + 1
        if (confirmations >= reqConf) {
          Log.info(
            """after new chain head {}, tx {} have {} confirmations out of {} required:
              |reporting to the observer""".stripMargin,
            Seq(currentHeight, txHash, confirmations, reqConf))
          confirmationSubscriptions -= txHash
          listener.confirmed(txHash, confirmations)
        } else {
          Log.info(
            """after new chain head {}, tx {} have {} confirmations out of {} required:
              |still waiting for more blocks""".stripMargin,
            Seq(currentHeight, txHash, confirmations, reqConf))
        }
      case _ =>
    }
  }

  private def notifyHeight(currentHeight: Int): Unit = {
    heightSubscriptions.foreach { case subscription @ HeightSubscription(height, listener) =>
      if (currentHeight >= height) {
        heightSubscriptions -= subscription
        listener.heightReached(currentHeight)
      }
    }
  }
}

private[blockchain] object BlockchainNotifier {
  private val Log = LoggerFactory.getLogger(classOf[BlockchainNotifier])

  trait ConfirmationListener {
    def rejected(tx: Hash): Unit
    def confirmed(tx: Hash, confirmations: Int): Unit
  }

  trait HeightListener {
    def heightReached(currentHeight: Long): Unit
  }
}
