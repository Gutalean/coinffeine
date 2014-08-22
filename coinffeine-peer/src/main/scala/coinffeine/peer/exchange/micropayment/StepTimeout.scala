package coinffeine.peer.exchange.micropayment

import scala.concurrent.duration.FiniteDuration

import akka.actor.{Actor, Cancellable}

import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.StepSignatureTimeout

private[micropayment] trait StepTimeout { this: Actor =>

  private var stepTimeout: Option[Cancellable] = None

  protected def scheduleStepTimeout(delay: FiniteDuration): Unit = {
    import context.dispatcher
    stepTimeout = Some(context.system.scheduler.scheduleOnce(
        delay = delay,
        receiver = self,
        message = StepSignatureTimeout
    ))
  }

  protected def cancelTimeout(): Unit = stepTimeout.map(_.cancel())
}
