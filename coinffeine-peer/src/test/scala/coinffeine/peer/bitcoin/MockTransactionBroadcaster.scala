package coinffeine.peer.bitcoin

import scala.util.control.NonFatal

import com.google.common.util.concurrent.{ListenableFuture, SettableFuture}
import org.bitcoinj.core.TransactionBroadcaster

import coinffeine.model.bitcoin.MutableTransaction

class MockTransactionBroadcaster extends TransactionBroadcaster {

  private var onBroadcast: MutableTransaction => Option[MutableTransaction] = recallTransaction
  private var _lastBroadcast: Option[MutableTransaction] = None

  override def broadcastTransaction(tx: MutableTransaction): ListenableFuture[MutableTransaction] = {
    val result = SettableFuture.create[MutableTransaction]()
    try {
      onBroadcast(tx).foreach(result.set)
    } catch {
      case NonFatal(e) => result.setException(e)
    }
    result
  }

  def lastBroadcast: Option[MutableTransaction] = _lastBroadcast

  def givenSuccessOnTransactionBroadcast(): Unit = synchronized {
    onBroadcast = recallTransaction
  }

  def givenErrorOnTransactionBroadcast(error: Throwable): Unit = synchronized {
    onBroadcast = throwError(error)
  }

  def givenTemporaryErrorOnTransactionBroadcast(error: Throwable): Unit = synchronized {
    onBroadcast = tx => {
      givenSuccessOnTransactionBroadcast()
      throwError(error)(tx)
    }
  }

  def givenNoResponseOnTransactionBroadcast(): Unit = synchronized {
    onBroadcast = _ => None
  }

  private def recallTransaction(tx: MutableTransaction) = synchronized {
    _lastBroadcast = Some(tx)
    Some(tx)
  }

  private def throwError(error: Throwable)(tx: MutableTransaction) = throw error
}
