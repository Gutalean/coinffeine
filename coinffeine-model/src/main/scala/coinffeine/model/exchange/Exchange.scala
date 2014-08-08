package coinffeine.model.exchange

import coinffeine.model.bitcoin._
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor

/** All the necessary information to start an exchange between two peers. This is the point of view
  * of the parts before handshaking and also of the brokers.
  */
trait Exchange[+C <: FiatCurrency] {
  /** An identifier for the exchange */
  val id: ExchangeId
  val role: Role
  val counterpartId: PeerId
  val amounts: Exchange.Amounts[C]
  val blockedFunds: Exchange.BlockedFunds
  /** Configurable parameters */
  val parameters: Exchange.Parameters
  val brokerId: PeerId
  val progress: Exchange.Progress[C]
}

object Exchange {

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
    require(bitcoinAmount.isPositive, s"bitcoin amount must be positive ($bitcoinAmount given)")
    require(fiatAmount.isPositive, s"fiat amount must be positive ($fiatAmount given)")

    val currency = fiatAmount.currency

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

  /** Funds reserved for the order this exchange belongs to */
  case class BlockedFunds(fiat: Option[PaymentProcessor.BlockedFundsId], bitcoin: BlockedCoinsId)

  case class Deposits(transactions: Both[ImmutableTransaction])

  case class Progress[+C <: FiatCurrency](
      bitcoinsTransferred: BitcoinAmount, fiatTransferred: CurrencyAmount[C]) {
    override def toString = s"progressed $bitcoinsTransferred by $fiatTransferred"
  }

  def noProgress[C <: FiatCurrency](c: C) = Exchange.Progress(Bitcoin.Zero, c.Zero)
}
