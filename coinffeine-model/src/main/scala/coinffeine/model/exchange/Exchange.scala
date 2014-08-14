package coinffeine.model.exchange

import coinffeine.model.bitcoin._
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{CurrencyAmount, BitcoinAmount, FiatCurrency}
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor

case class Exchange[+C <: FiatCurrency, +S <: Exchange.State[C]](
    id: ExchangeId,
    role: Role,
    counterpartId: PeerId,
    amounts: Exchange.Amounts[C],
    parameters: Exchange.Parameters,
    brokerId: PeerId,
    blockedFunds: Exchange.BlockedFunds,
    state: S) {

  val currency: C = amounts.fiatAmount.currency

  val progress: Exchange.Progress[C] = state.progress
  require(progress.bitcoinsTransferred <= amounts.bitcoinAmount,
    "invalid running exchange instantiation: " +
      s"progress $progress is inconsistent with amounts $amounts")
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

  case class Progress[+C <: FiatCurrency](bitcoinsTransferred: BitcoinAmount,
                                          fiatTransferred: CurrencyAmount[C]) {

    def +[C2 >: C <: FiatCurrency](other: Progress[C2]) = Progress(
      bitcoinsTransferred = bitcoinsTransferred + other.bitcoinsTransferred,
      fiatTransferred = fiatTransferred + other.fiatTransferred
    )

    override def toString = s"progressed $bitcoinsTransferred by $fiatTransferred"
  }

  def noProgress[C <: FiatCurrency](c: C) = Exchange.Progress(Bitcoin.Zero, c.Zero)


  def notStarted[C <: FiatCurrency](id: ExchangeId,
                                    role: Role,
                                    counterpartId: PeerId,
                                    amounts: Exchange.Amounts[C],
                                    parameters: Exchange.Parameters,
                                    brokerId: PeerId,
                                    blockedFunds: Exchange.BlockedFunds) =
    Exchange(id, role, counterpartId, amounts, parameters, brokerId, blockedFunds,
      NotStarted()(amounts.currency))

  sealed trait State[+C <: FiatCurrency] {
    val progress: Exchange.Progress[C]
  }

  case class NotStarted[C <: FiatCurrency]()(currency: C) extends State[C] {
    override val progress = Exchange.noProgress(currency)
  }

  implicit class NonStartedTransitions[C <: FiatCurrency](val exchange: Exchange[C, NotStarted[C]])
    extends AnyVal {

    def startHandshaking(user: Exchange.PeerInfo,
                         counterpart: Exchange.PeerInfo): Exchange[C, Handshaking[C]] =
      exchange.copy(state = Handshaking(user, counterpart)(exchange.currency))
  }

  case class Handshaking[C <: FiatCurrency](user: Exchange.PeerInfo, counterpart: Exchange.PeerInfo)
                                           (currency: C) extends State[C] with StartedHandshake {
    override val progress = Exchange.noProgress(currency)
  }

  implicit class HandshakingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Handshaking[C]])
    extends AnyVal {

    def startExchanging(deposits: Exchange.Deposits): Exchange[C, Exchanging[C]] =
      exchange.copy(state = Exchanging(exchange.currency, exchange.state, deposits))
  }

  case class Exchanging[C <: FiatCurrency](
      user: Exchange.PeerInfo,
      counterpart: Exchange.PeerInfo,
      deposits: Exchange.Deposits,
      progress: Exchange.Progress[C])(currency: C)
    extends State[C] with StartedExchange

  object Exchanging {
    def apply[C <: FiatCurrency](currency: C,
                                 previousState: Handshaking[C],
                                 deposits: Exchange.Deposits): Exchanging[C] =
      Exchanging(previousState.user, previousState.counterpart, deposits,
        Exchange.noProgress(currency))(currency)
  }

  implicit class ExchangingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Exchanging[C]])
    extends AnyVal {

    def complete: Exchange[C, Completed[C]] =
      exchange.copy(state = Completed(exchange.amounts, exchange.state))

    def increaseProgress(btcAmount: BitcoinAmount,
                         fiatAmount: CurrencyAmount[C]): Exchange[C, Exchanging[C]] = {
      val progress = exchange.state.progress + Exchange.Progress(btcAmount, fiatAmount)
      exchange.copy(state = exchange.state.copy(progress = progress)(exchange.currency))
    }
  }

  case class Completed[C <: FiatCurrency](user: Exchange.PeerInfo,
                                          counterpart: Exchange.PeerInfo,
                                          deposits: Exchange.Deposits)(amounts: Exchange.Amounts[C])
    extends State[C] with StartedExchange {
    override val progress = Progress(amounts.bitcoinAmount, amounts.fiatAmount)
  }

  object Completed {
    def apply[C <: FiatCurrency](amounts: Exchange.Amounts[C],
                                 previousState: Exchanging[C]): Completed[C] =
      Completed(previousState.user, previousState.counterpart, previousState.deposits)(amounts)
  }

  trait StartedHandshake {
    val user: Exchange.PeerInfo
    val counterpart: Exchange.PeerInfo

    require(user.bitcoinKey.hasPrivKey)
  }

  implicit class StartedHandshakePimps(val exchange: Exchange[_, _ <: StartedHandshake])
    extends AnyVal {

    def participants: Both[Exchange.PeerInfo] = Both.fromSeq(exchange.role match {
      case BuyerRole => Seq(exchange.state.user, exchange.state.counterpart)
      case SellerRole => Seq(exchange.state.counterpart, exchange.state.user)
    })

    def requiredSignatures: Both[PublicKey] = participants.map(_.bitcoinKey)
  }

  trait StartedExchange extends StartedHandshake {
    val deposits: Exchange.Deposits
  }
}
