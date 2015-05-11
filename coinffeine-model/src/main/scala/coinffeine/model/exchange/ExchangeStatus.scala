package coinffeine.model.exchange

import coinffeine.model.Both
import coinffeine.model.bitcoin.Hash

sealed trait ExchangeStatus {
  def name: String
}

object ExchangeStatus {

  case object Handshaking extends ExchangeStatus {
    override def name = "handshaking"
  }

  case class WaitingDepositConfirmation(user: Exchange.PeerInfo,
                                        counterpart: Exchange.PeerInfo) extends ExchangeStatus {
    override def name = "waiting deposit confirmation"
  }

  case class Exchanging(deposits: Both[Hash]) extends ExchangeStatus {
    override def name = "exchanging"
  }

  case object Successful extends ExchangeStatus {
    override def name = "success"
  }

  case class Failed(cause: FailureCause) extends ExchangeStatus {
    override def name = "failed"
  }

  case class Aborting(cause: AbortionCause) extends ExchangeStatus {
    override def name = "aborting"
  }
}
