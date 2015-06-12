package coinffeine.peer.bitcoin.blockchain

import scala.collection.JavaConversions._

import com.typesafe.scalalogging.StrictLogging
import org.bitcoinj.core._

import coinffeine.model.bitcoin.ImmutableTransaction

private[blockchain] class WalletNotifier
  extends AbstractWalletEventListener with StrictLogging {

  import WalletNotifier._

  private var outputSubscriptions: Map[TransactionOutPoint, Set[OutputListener]] =
    Map.empty.withDefaultValue(Set.empty)

  def watchOutput(output: TransactionOutPoint, listener: OutputListener): Unit = synchronized {
    logger.debug(s"Watching $output output")
    val updatedListeners = outputSubscriptions(output) + listener
    outputSubscriptions += output -> updatedListeners
  }

  override def onCoinsSent(
    wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {
    notifySpentOutputs(tx)
  }

  override def onCoinsReceived(
    wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {
    notifySpentOutputs(tx)
  }

  private def notifySpentOutputs(tx: Transaction): Unit = {
    if (outputSubscriptions.nonEmpty) {
      logger.debug("Checking for {} spending watched outputs.", tx.getHashAsString)
    }
    for (input <- tx.getInputs) {
      val outPoint = input.getOutpoint
      val relevantSubscriptions = outputSubscriptions(outPoint)
      relevantSubscriptions.foreach { subscription =>
        subscription.outputSpent(ImmutableTransaction(tx))
      }
      if (relevantSubscriptions.nonEmpty) {
        logger.info("{} was spent by {}", outPoint, tx.getHashAsString)
        outputSubscriptions -= outPoint
      }
    }
  }
}

private[blockchain] object WalletNotifier {
  trait OutputListener {
    def outputSpent(tx: ImmutableTransaction): Unit
  }
}
