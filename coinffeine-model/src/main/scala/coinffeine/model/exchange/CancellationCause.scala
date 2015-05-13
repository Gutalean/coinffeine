package coinffeine.model.exchange

sealed trait CancellationCause

object CancellationCause {

  case object UserCancellation extends CancellationCause {
    override def toString = "cancelled by user"
  }

  case object CannotStartHandshake extends CancellationCause {
    override def toString = "cancelled by handshake start issues"
  }

  case class HandshakeFailed(cause: HandshakeFailureCause) extends CancellationCause {
    override def toString = "handshake failed"
  }
}
