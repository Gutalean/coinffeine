package com.coinffeine.common.exchange

import java.security.SecureRandom
import scala.util.Random

import com.coinffeine.common._
import com.coinffeine.common.bitcoin._
import com.coinffeine.common.exchange.Exchange.Deposits
import com.coinffeine.common.paymentprocessor.PaymentProcessor

/** All the necessary information to start an exchange between two peers. This is the point of view
  * of the parts before handshaking and also of the brokers.
  */
trait Exchange[+C <: FiatCurrency] {
  /** An identifier for the exchange */
  val id: Exchange.Id
  val amounts: Exchange.Amounts[C]
  /** Configurable parameters */
  val parameters: Exchange.Parameters
  /** Identifiers of the buyer and the seller */
  val peerIds: Both[PeerId]
  val brokerId: PeerId
}

/** Relevant information for an ongoing exchange. This point of view is only held by the parts
  * as contains information not made public to everyone on the network.
  */
trait OngoingExchange[+C <: FiatCurrency] extends Exchange[C] {
  val role: Role

  /** Information about the parts */
  val participants: Both[Exchange.PeerInfo]

  def requiredSignatures: Both[PublicKey] = participants.map(_.bitcoinKey)

  val user = participants(role)
  val counterpart = participants(role.counterpart)

  require(user.bitcoinKey.hasPrivKey)
}

case class NonStartedExchange[+C <: FiatCurrency](
    override val id: Exchange.Id,
    override val amounts: Exchange.Amounts[C],
    override val parameters: Exchange.Parameters,
    override val peerIds: Both[PeerId],
    override val brokerId: PeerId) extends Exchange[C]

/** Relevant information during the handshake of an exchange. This point of view is only held by
  * the parts as contains information not made public to everyone on the network. */
case class HandshakingExchange[+C <: FiatCurrency](
    override val role: Role,
    override val id: Exchange.Id,
    override val amounts: Exchange.Amounts[C],
    override val parameters: Exchange.Parameters,
    override val peerIds: Both[PeerId],
    override val brokerId: PeerId,
    override val participants: Both[Exchange.PeerInfo]) extends OngoingExchange[C] {
}

object HandshakingExchange {
  def apply[C <: FiatCurrency](role: Role, user: Exchange.PeerInfo, counterpart: Exchange.PeerInfo,
                               exchange: Exchange[C]): HandshakingExchange[C] = {
    import exchange._
    val participants = Both(
      buyer = role.buyer(user, counterpart),
      seller = role.seller(user, counterpart)
    )
    HandshakingExchange(role, id, amounts, parameters, peerIds, brokerId, participants)
  }
}

case class RunningExchange[+C <: FiatCurrency](
  override val role: Role,
  override val id: Exchange.Id,
  override val amounts: Exchange.Amounts[C],
  override val parameters: Exchange.Parameters,
  override val peerIds: Both[PeerId],
  override val brokerId: PeerId,
  override val participants: Both[Exchange.PeerInfo],
  deposits: Deposits) extends OngoingExchange[C] {

}

object RunningExchange {

  def apply[C <: FiatCurrency](deposits: Deposits,
                               exchange: HandshakingExchange[C]): RunningExchange[C] = {
    import exchange._
    RunningExchange(role, id, amounts, parameters, peerIds, brokerId, participants, deposits)
  }
}

object Exchange {

  case class Id(value: String) {
    override def toString = s"exchange:$value"
  }

  object Id {
    private val secureGenerator = new Random(new SecureRandom())

    def random() = Id(value = secureGenerator.nextString(12))
  }

  /** Configurable parameters of an exchange.
    *
    * @param lockTime  The block number which will cause the refunds transactions to be valid
    * @param network   Bitcoin network
    */
  case class Parameters(lockTime: Long, network: Network)

  case class PeerInfo(paymentProcessorAccount: PaymentProcessor.AccountId, bitcoinKey: KeyPair)

  /** How the exchange is break down into steps */
  case class StepBreakdown(intermediateSteps: Int) {
    require(intermediateSteps > 0, s"Intermediate steps must be positive ($intermediateSteps given)")
    val totalSteps = intermediateSteps + 1
  }

  case class Amounts[+C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                         fiatAmount: CurrencyAmount[C],
                                         breakdown: Exchange.StepBreakdown) {
    require(bitcoinAmount.isPositive,
      s"bitcoin amount must be positive ($bitcoinAmount given)")
    require(fiatAmount.isPositive,
      s"fiat amount must be positive ($fiatAmount given)")

    /** Amount of bitcoins to exchange per intermediate step */
    val stepBitcoinAmount: BitcoinAmount = bitcoinAmount / breakdown.intermediateSteps
    /** Amount of fiat to exchange per intermediate step */
    val stepFiatAmount: CurrencyAmount[C] = fiatAmount / breakdown.intermediateSteps

    /** Total amount compromised in multisignature by the buyer */
    val buyerDeposit: BitcoinAmount = stepBitcoinAmount * 2
    /** Amount refundable by the buyer after a lock time */
    val buyerRefund: BitcoinAmount = buyerDeposit - stepBitcoinAmount

    /** Total amount compromised in multisignature by the seller */
    val sellerDeposit: BitcoinAmount = bitcoinAmount + stepBitcoinAmount
    /** Amount refundable by the seller after a lock time */
    val sellerRefund: BitcoinAmount = sellerDeposit - stepBitcoinAmount
  }

  case class Deposits(transactions: Both[ImmutableTransaction])
}
