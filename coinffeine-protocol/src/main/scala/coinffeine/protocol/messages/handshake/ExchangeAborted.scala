package coinffeine.protocol.messages.handshake

import coinffeine.model.bitcoin.Hash
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

case class ExchangeAborted(exchangeId: ExchangeId, cause: ExchangeAborted.Cause) extends PublicMessage

case object ExchangeAborted {

  sealed trait Cause {
    def message: String
  }

  case class Rejected(rejectionCause: ExchangeRejection.Cause) extends Cause {
    override def message = s"Rejected by counterpart: ${rejectionCause.message}"
  }

  case class PublicationFailure(tx: Hash) extends Cause {
    override def message = s"Cannot publish $tx"
  }

  case class InvalidCommitments(culprits: Set[PeerId]) extends Cause {
    require(culprits.nonEmpty && culprits.size <= 2)
    override def message = "Invalid commitment%s from %s".format(
      if (culprits.size > 1) "s" else "",
      culprits.toSeq.sortBy(_.value).mkString(" and ")
    )
  }

  object InvalidCommitments {
    def apply(culprits: PeerId*): InvalidCommitments = InvalidCommitments(culprits.toSet)
  }

  case object Timeout extends Cause {
    override def message = "Timeout waiting for peers"
  }
}
