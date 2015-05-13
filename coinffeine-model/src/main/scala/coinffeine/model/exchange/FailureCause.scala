package coinffeine.model.exchange

sealed trait FailureCause

object FailureCause {

  case class Cancellation(cause: CancellationCause) extends FailureCause {
    override val toString = cause.toString
  }

  case class Abortion(cause: AbortionCause) extends FailureCause {
    override val toString = cause.toString
  }

  case object PanicBlockReached extends FailureCause {
    override val toString = "panic blocked reached"
  }

  case class StepFailed(step: Int) extends FailureCause {
    override val toString = s"step $step failed"
  }

  case object UnexpectedBroadcast extends FailureCause {
    override val toString = "unexpected transaction broadcast"
  }

  case object NoBroadcast extends FailureCause {
    override val toString = "missing transaction broadcast"
  }

  case class HandshakeFailed(cause: Throwable) extends FailureCause {
    override val toString = "handshake failed"
  }
}
