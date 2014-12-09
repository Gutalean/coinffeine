package coinffeine.model.exchange

sealed trait CancellationCause

object CancellationCause {

  case object UserCancellation extends CancellationCause {
    override val toString = "cancelled by user"
  }

  case class CannotStartHandshake(cause: Throwable) extends CancellationCause {
    override val toString = "cancelled by handshake start issues"
  }

  case class HandshakeFailed(cause: Throwable) extends CancellationCause {
    override val toString = "handshake failed"
  }
}
