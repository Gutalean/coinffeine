package coinffeine.model.exchange

sealed trait HandshakeFailureCause

object HandshakeFailureCause {
  case object SignatureTimeout extends HandshakeFailureCause
  case object BrokerAbortion extends HandshakeFailureCause
  case object CannotCreateDeposits extends HandshakeFailureCause
  case object InvalidCounterpartAccountId extends HandshakeFailureCause
}
