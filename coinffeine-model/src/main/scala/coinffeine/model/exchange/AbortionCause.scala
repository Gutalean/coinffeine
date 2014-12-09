package coinffeine.model.exchange

import scala.util.Try

sealed trait AbortionCause

object AbortionCause {

  case class HandshakeWithCommitmentFailed(cause: Throwable) extends AbortionCause {
    override val toString = "aborted by failed handshake commitment"
  }

  case class InvalidCommitments(validation: Both[Try[Unit]]) extends AbortionCause {
    require(validation.toSeq.count(_.isFailure) > 0)
    override val toString = "aborted by invalid commitments"
  }
}
