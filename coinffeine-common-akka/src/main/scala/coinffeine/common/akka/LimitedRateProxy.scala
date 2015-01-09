package coinffeine.common.akka

import scala.concurrent.duration._

import akka.actor._

/** Place this actor in front of another to limit the rate of incoming messages.
  *
  * In the case of a burst of messages, only the most recent will be delivered.
  *
  * @constructor
  * @param receiver  Messages will be forwarded to this ref
  * @param minTimeBetweenMessages  No two messages will arrive faster than this time
  */
private[this] class LimitedRateProxy(receiver: ActorRef, minTimeBetweenMessages: FiniteDuration)
  extends Actor {

  import context.dispatcher

  private case object SendDelayedMessage
  private case class DelayedSend(sender: ActorRef, message: Any)
  private var cannotSendUntil = now()
  private var sendTimer: Option[Cancellable] = None
  private var pendingSend: Option[DelayedSend] = None

  override def postStop(): Unit = {
    sendTimer.foreach(_.cancel())
  }

  override def receive: Receive = {
    case SendDelayedMessage =>
      sendTimer = None
      pendingSend.foreach { send =>
        doSend(send.message, send.sender)
      }
      pendingSend = None

    case message =>
      if (shouldDelayMessage) delaySending(message)
      else doSend(message, sender())
  }

  private def shouldDelayMessage: Boolean = now() < cannotSendUntil

  private def delaySending(message: Any): Unit = {
    if (pendingSend.isEmpty) {
      scheduleTimer()
    }
    pendingSend = Some(DelayedSend(sender(), message))
  }

  private def scheduleTimer(): Unit = {
    val delay = (cannotSendUntil - now()).millis
    sendTimer = Some(context.system.scheduler.scheduleOnce(delay, self, SendDelayedMessage))
  }

  private def doSend(message: Any, from: ActorRef): Unit = {
    receiver.tell(message, from)
    cannotSendUntil = now() + minTimeBetweenMessages.toMillis
  }

  private def now() = System.currentTimeMillis()
}

object LimitedRateProxy {
  def props(receiver: ActorRef, minTimeBetweenMessages: FiniteDuration): Props = {
    require(minTimeBetweenMessages > 0.millis)
    Props(new LimitedRateProxy(receiver, minTimeBetweenMessages))
  }
}
