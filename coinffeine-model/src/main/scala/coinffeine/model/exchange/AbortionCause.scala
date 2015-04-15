package coinffeine.model.exchange

import coinffeine.model.Both

sealed trait AbortionCause

object AbortionCause {

  case class HandshakeWithCommitmentFailed(cause: Throwable) extends AbortionCause {
    override val toString = "aborted by failed handshake commitment"
  }

  case class InvalidCommitments(validation: Both[DepositValidation]) extends AbortionCause {
    require(validation.toSeq.count(_.isFailure) > 0)

    override val toString = "aborted by invalid " + (validation.map(_.isFailure) match {
      case Both(true, true) => "commitments"
      case Both(true, _) => "buyer commitment"
      case _ => "seller commitment"
    })
  }
}
