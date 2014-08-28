package coinffeine.peer

import scala.concurrent.duration._
import scala.language.postfixOps

/** Protocol constants with default values.
  *
  * @constructor
  * @param commitmentConfirmations  Minimum number of confirmations to trust commitment TXs
  * @param resubmitHandshakeMessagesTimeout  Handshake requests are resubmitted after this timeout
  * @param refundSignatureAbortTimeout  Handshake is aborted after this time from handshake start
  * @param commitmentAbortTimeout  Maximum time than a broker will wait for buyer and seller
  *                                commitments
  * @param exchangeSignatureTimeout Amount of time the actor will wait for a step signature
  * @param exchangePaymentProofTimeout Amount of time the actor will wait for a payment proof
  * @param microPaymentChannelResubmitTimeout  Amount of time before resubmitting information
  *                                            during the micro payment channel exchange
  * @param orderExpirationInterval Time that orders take to be discarded if not renewed
  * @param orderResubmitInterval   Open orders should be resubmitted after this interval to avoid
  *                                being discarded
  * @param refundLockTime         The number of blocks to wait for the refund transactions to be
  *                               valid
  * @param refundSafetyBlockCount The number of blocks before the refund can be broadcast where we
  *                               want to finish the exchange forcefully.
  */
case class ProtocolConstants(
  commitmentConfirmations: Int = 1,
  resubmitHandshakeMessagesTimeout: FiniteDuration = 10 seconds,
  refundSignatureAbortTimeout: FiniteDuration = 5 minutes,
  commitmentAbortTimeout: FiniteDuration = 5 minutes,
  exchangeSignatureTimeout: FiniteDuration = 5 minutes,
  exchangePaymentProofTimeout: FiniteDuration = 5 minutes,
  microPaymentChannelResubmitTimeout: FiniteDuration = 3 seconds,
  orderExpirationInterval: FiniteDuration = 1 minute,
  orderResubmitInterval: FiniteDuration = 30 seconds,
  orderAcknowledgeTimeout: FiniteDuration = 15 seconds,
  refundLockTime: Int = 10,
  refundSafetyBlockCount: Int = 2
)

object ProtocolConstants {

  val Default = ProtocolConstants()

  trait Component {
    val protocolConstants: ProtocolConstants
  }

  trait DefaultComponent extends Component {
    override val protocolConstants = Default
  }
}
