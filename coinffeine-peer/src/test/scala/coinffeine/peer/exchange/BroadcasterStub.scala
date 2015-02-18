package coinffeine.peer.exchange

import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

import akka.actor.{Actor, Props, ReceiveTimeout}

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.bitcoin.BitcoinPeerActor.TransactionPublished
import coinffeine.peer.exchange.broadcast.ExchangeTransactionBroadcaster._

class BroadcasterStub(transaction: Try[ImmutableTransaction], timeout: Duration) extends Actor {

  override def preStart(): Unit = {
    context.setReceiveTimeout(timeout)
  }

  override val receive: Receive = {
    case PublishBestTransaction | ReceiveTimeout =>
      sender() ! (transaction match {
        case Success(tx) => SuccessfulBroadcast(TransactionPublished(tx, tx))
        case Failure(cause) => FailedBroadcast(cause)
      })
  }
}

object BroadcasterStub {
  def broadcasting(tx: ImmutableTransaction, timeout: Duration = Duration.Undefined) =
    Props(new BroadcasterStub(Success(tx), timeout))

  private val exception = new Exception("injected broadcast failure") with NoStackTrace

  def failing = Props(new BroadcasterStub(Failure(exception), Duration.Undefined))
}
