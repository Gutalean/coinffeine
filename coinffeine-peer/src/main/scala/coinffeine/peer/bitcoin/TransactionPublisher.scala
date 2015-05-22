package coinffeine.peer.bitcoin

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import akka.actor._
import akka.pattern._
import org.bitcoinj.core.TransactionBroadcaster

import coinffeine.common.GuavaFutureImplicits
import coinffeine.model.bitcoin.ImmutableTransaction

private[bitcoin] class TransactionPublisher(
  originalTx: ImmutableTransaction,
  transactionBroadcaster: TransactionBroadcaster,
  listener: ActorRef,
  rebroadcastTimeout: FiniteDuration) extends Actor with ActorLogging with GuavaFutureImplicits {

  import context.dispatcher
  import TransactionPublisher.MaxAttempts

  private case class AttemptSuccess(broadcastTx: ImmutableTransaction)
  private case class AttemptFailure(attempt: Int, cause: Throwable)
  private case class AttemptTimeout(attempt: Int)

  private var currentAttempt = 0
  private var attemptTimeout: Option[Cancellable] = None

  override def preStart(): Unit = {
    nextAttempt()
  }

  override def receive: Receive = {
    case AttemptSuccess(broadcastTx) =>
      finishWith(BitcoinPeerActor.TransactionPublished(originalTx, broadcastTx))

    case AttemptFailure(MaxAttempts, cause) =>
      log.error(cause, "Attempt {} of {} broadcast failed", MaxAttempts, originalTx.get.getHash)
      finishWith(BitcoinPeerActor.TransactionNotPublished(originalTx, cause))

    case AttemptFailure(attempt, cause) if attempt == currentAttempt =>
      log.error(cause, "Attempt {} of {} broadcast failed", attempt, originalTx.get.getHash)
      nextAttempt()

    case AttemptTimeout(attempt) if attempt == currentAttempt && attempt < MaxAttempts =>
      log.error("Attempt {} of {} broadcast timed out", attempt, originalTx.get.getHash)
      nextAttempt()
  }

  private def nextAttempt(): Unit = {
    clearTimeout()
    currentAttempt += 1
    scheduleAttemptTimeout()
    attemptToBroadcast()
  }

  private def scheduleAttemptTimeout(): Unit = {
    attemptTimeout = Some(context.system.scheduler.scheduleOnce(
      delay = rebroadcastTimeout,
      receiver = self,
      message = AttemptTimeout(currentAttempt)
    ))
  }

  private def clearTimeout(): Unit = {
    attemptTimeout.foreach(_.cancel())
    attemptTimeout = None
  }

  private def attemptToBroadcast(): Unit = {
    log.info("Attempt {} of broadcasting {}", currentAttempt, originalTx)
    val attempt = currentAttempt
    transactionBroadcaster.broadcastTransaction(originalTx.get).toScala
      .map(broadcastTx => AttemptSuccess(ImmutableTransaction(broadcastTx)))
      .recover {
        case NonFatal(cause) => AttemptFailure(attempt, cause)
      }.pipeTo(self)
  }

  private def finishWith(notification: Any): Unit = {
    clearTimeout()
    listener ! notification
    context.stop(self)
  }
}

private object TransactionPublisher {
  val MaxAttempts = 3
}
