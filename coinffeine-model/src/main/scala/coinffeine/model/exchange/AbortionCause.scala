package coinffeine.model.exchange

import coinffeine.model.Both

sealed trait AbortionCause

object AbortionCause {

  case object HandshakeCommitmentsFailure extends AbortionCause {
    override def toString = "aborted by failed handshake commitment"
  }

  case class InvalidCommitments(invalidCommitments: Both[Boolean]) extends AbortionCause {
    require(invalidCommitments.toSeq.exists(!_), "At least one invalid commitment is required")

    override def toString = "aborted by invalid " + (invalidCommitments match {
      case Both(true, true) => "commitments"
      case Both(true, _) => "buyer commitment"
      case _ => "seller commitment"
    })
  }
}
