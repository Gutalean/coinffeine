package coinffeine.peer.bitcoin

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern._
import org.bitcoinj.core.TransactionBroadcaster

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.peer.utils.FutureUtils

class TransactionPublisher(originalTx: ImmutableTransaction,
                           transactionBroadcaster: TransactionBroadcaster,
                           listener: ActorRef,
                           rebroadcastTimeout: FiniteDuration)
  extends Actor with ActorLogging with FutureUtils {

  import context.dispatcher
  import TransactionPublisher.MaxAttempts

  private case class AttemptSuccess(broadcastTx: ImmutableTransaction)
  private case class AttemptFailure(attempt: Int, cause: Throwable)
  private case class AttemptTimeout(attempt: Int)

  private var currentAttempt = 0

  override def preStart(): Unit = {
    nextAttempt()
  }

  override def receive: Receive = {
    case AttemptSuccess(broadcastTx) =>
      finishWith(BitcoinPeerActor.TransactionPublished(originalTx, broadcastTx))

    case AttemptFailure(MaxAttempts, cause) =>
      finishWith(BitcoinPeerActor.TransactionNotPublished(originalTx, cause))

    case AttemptFailure(attempt, cause) if attempt == currentAttempt =>
      log.error(cause, "Attempt {} of {} broadcast failed", attempt, originalTx.get.getHash)
      nextAttempt()

    case AttemptTimeout(attempt) if attempt == currentAttempt && attempt < MaxAttempts =>
      log.error("Attempt {} of {} broadcast timed out", attempt, originalTx.get.getHash)
      nextAttempt()
  }

  private def nextAttempt(): Unit = {
    currentAttempt += 1
    scheduleAttemptTimeout()
    attemptToBroadcast()
  }

  private def scheduleAttemptTimeout(): Unit = {
    context.system.scheduler.scheduleOnce(
      delay = rebroadcastTimeout,
      receiver = self,
      message = AttemptTimeout(currentAttempt)
    )
  }

  private def attemptToBroadcast(): Unit = {
    val attempt = currentAttempt
    transactionBroadcaster.broadcastTransaction(originalTx.get).toScala
      .map(broadcastTx => AttemptSuccess(ImmutableTransaction(broadcastTx)))
      .recover {
        case NonFatal(cause) => AttemptFailure(attempt, cause)
      }.pipeTo(self)
  }

  private def finishWith(notification: Any): Unit = {
    listener ! notification
    context.stop(self)
  }
}

private object TransactionPublisher {
  val MaxAttempts = 3
}
