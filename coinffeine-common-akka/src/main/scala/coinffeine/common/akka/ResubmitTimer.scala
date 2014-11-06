package coinffeine.common.akka

import scala.concurrent.duration.FiniteDuration

import akka.actor.{ActorContext, Cancellable}

/** Sends periodically a [[ResubmitTimer.ResubmitTimeout]] as a self-message for
  * message resubmission.
  */
class ResubmitTimer(context: ActorContext, timeout: FiniteDuration) {

  private var timer: Option[Cancellable] = None

  def start(): Unit = {
    import context.dispatcher
    timer = Some(context.system.scheduler.schedule(
      initialDelay = timeout,
      interval = timeout,
      receiver = context.self,
      message = ResubmitTimer.ResubmitTimeout
    ))
  }

  def cancel(): Unit = {
    timer.foreach(_.cancel())
  }

  /** Reset the time to wait until the next timeout */
  def reset(): Unit = {
    cancel()
    start()
  }
}

object ResubmitTimer {
  case object ResubmitTimeout
}
