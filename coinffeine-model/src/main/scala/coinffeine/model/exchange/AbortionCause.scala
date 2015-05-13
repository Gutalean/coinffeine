package coinffeine.model.exchange

import coinffeine.model.Both

sealed trait AbortionCause

object AbortionCause {

  case object HandshakeCommitmentsFailure extends AbortionCause {
    override def toString = "aborted by failed handshake commitment"
  }

  case class InvalidCommitments(validation: Both[DepositValidation]) extends AbortionCause {
    require(validation.toSeq.count(_.isFailure) > 0)

    override def toString = "aborted by invalid " + (validation.map(_.isFailure) match {
      case Both(true, true) => "commitments"
      case Both(true, _) => "buyer commitment"
      case _ => "seller commitment"
    })
  }
}
