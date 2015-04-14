package coinffeine.model.exchange

sealed trait ExchangeStatus {
  def name: String
}

object ExchangeStatus {

  case object Handshaking extends ExchangeStatus {
    override def name = "handshaking"
  }

  case object WaitingDepositConfirmation extends ExchangeStatus {
    override def name = "waiting deposit confirmation"
  }

  case object Exchanging extends ExchangeStatus {
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
