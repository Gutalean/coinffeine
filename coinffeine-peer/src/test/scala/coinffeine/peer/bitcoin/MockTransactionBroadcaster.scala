package coinffeine.peer.bitcoin

import scala.util.control.NonFatal

import com.google.common.util.concurrent.{ListenableFuture, SettableFuture}
import org.bitcoinj.core.TransactionBroadcaster

import coinffeine.model.bitcoin.MutableTransaction

class MockTransactionBroadcaster extends TransactionBroadcaster {

  private var onBroadcast: MutableTransaction => MutableTransaction = recallTransaction
  private var _lastBroadcast: Option[MutableTransaction] = None

  override def broadcastTransaction(tx: MutableTransaction): ListenableFuture[MutableTransaction] = {
    val result = SettableFuture.create[MutableTransaction]()
    try {
      result.set(onBroadcast(tx))
    } catch {
      case NonFatal(e) => result.setException(e)
    }
    result
  }

  def lastBroadcast: Option[MutableTransaction] = _lastBroadcast

  def givenSuccessOnTransactionBroadcast(): Unit = {
    onBroadcast = recallTransaction
  }

  def givenErrorOnTransactionBroadcast(error: Throwable): Unit = {
    onBroadcast = throwError(error)
  }

  private def recallTransaction(tx: MutableTransaction): MutableTransaction = {
    _lastBroadcast = Some(tx)
    tx
  }

  private def throwError(error: Throwable)(tx: MutableTransaction): MutableTransaction = {
    throw error
  }
}
