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
    blockedFunds: Exchange.BlockedFunds,
    state: S) {

  val currency: C = amounts.fiatExchanged.currency

  val progress: Exchange.Progress[C] = state.progress
  require(progress.bitcoinsTransferred <= amounts.bitcoinExchanged,
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

  /** Amounts involved on one exchange step */
  case class StepAmounts[+C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                             fiatAmount: CurrencyAmount[C]) {
    def +[C2 >: C <: FiatCurrency](other: StepAmounts[C2]) = StepAmounts(
      bitcoinAmount + other.bitcoinAmount,
      fiatAmount + other.fiatAmount
    )
  }

  /** Amounts of money involved on an exchange.
    *
    * @param deposits          Bitcoins deposited in multisign by each part
    * @param refunds           Amount refundable by each part after a lock time
    * @param bitcoinExchanged  Amount of bitcoins to be exchanged
    * @param fiatExchanged     Amount of fiat to be exchanged
    * @tparam C                Fiat currency defined to this exchange
    */
  case class Amounts[+C <: FiatCurrency](deposits: Both[BitcoinAmount],
                                         refunds: Both[BitcoinAmount],
                                         bitcoinExchanged: BitcoinAmount,
                                         fiatExchanged: CurrencyAmount[C],
                                         breakdown: Exchange.StepBreakdown) {
    require(bitcoinExchanged.isPositive, s"bitcoin amount must be positive ($bitcoinExchanged given)")
    require(fiatExchanged.isPositive, s"fiat amount must be positive ($fiatExchanged given)")

    val currency = fiatExchanged.currency

    /** Amounts to exchange per intermediate step */
    private val stepAmounts = StepAmounts(
      bitcoinAmount = bitcoinExchanged / breakdown.intermediateSteps,
      fiatAmount = fiatExchanged / breakdown.intermediateSteps
    )

    val steps = Seq.fill(breakdown.intermediateSteps)(stepAmounts)

    val fiatRequired = Both[CurrencyAmount[C]](buyer = fiatExchanged, seller = currency.Zero)
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
                                    blockedFunds: Exchange.BlockedFunds) = Exchange(
    id, role, counterpartId, amounts, parameters, blockedFunds, NotStarted()(amounts.currency))

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
                                           (currency: C) extends State[C] with StartedHandshake[C] {
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
      progress: Exchange.Progress[C])
    extends State[C] with StartedExchange[C]

  object Exchanging {
    def apply[C <: FiatCurrency](currency: C,
                                 previousState: Handshaking[C],
                                 deposits: Exchange.Deposits): Exchanging[C] =
      Exchanging(previousState.user, previousState.counterpart, deposits,
        Exchange.noProgress(currency))
  }

  implicit class ExchangingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Exchanging[C]])
    extends AnyVal {

    def complete: Exchange[C, Completed[C]] =
      exchange.copy(state = Completed(exchange.amounts, exchange.state))

    def increaseProgress(btcAmount: BitcoinAmount,
                         fiatAmount: CurrencyAmount[C]): Exchange[C, Exchanging[C]] = {
      val progress = exchange.state.progress + Exchange.Progress(btcAmount, fiatAmount)
      exchange.copy(state = exchange.state.copy(progress = progress))
    }
  }

  case class Completed[C <: FiatCurrency](user: Exchange.PeerInfo,
                                          counterpart: Exchange.PeerInfo,
                                          deposits: Exchange.Deposits)(amounts: Exchange.Amounts[C])
    extends State[C] with StartedExchange[C] {
    override val progress = Progress(amounts.bitcoinExchanged, amounts.fiatExchanged)
  }

  object Completed {
    def apply[C <: FiatCurrency](amounts: Exchange.Amounts[C],
                                 previousState: Exchanging[C]): Completed[C] =
      Completed(previousState.user, previousState.counterpart, previousState.deposits)(amounts)
  }

  trait StartedHandshake[C <: FiatCurrency] extends State[C] {
    val user: Exchange.PeerInfo
    val counterpart: Exchange.PeerInfo

    require(user.bitcoinKey.hasPrivKey)
  }

  implicit class StartedHandshakePimps(val exchange: Exchange[_, StartedHandshake[_]])
    extends AnyVal {

    def participants: Both[Exchange.PeerInfo] = Both.fromSeq(exchange.role match {
      case BuyerRole => Seq(exchange.state.user, exchange.state.counterpart)
      case SellerRole => Seq(exchange.state.counterpart, exchange.state.user)
    })

    def requiredSignatures: Both[PublicKey] = participants.map(_.bitcoinKey)
  }

  trait StartedExchange[C <: FiatCurrency] extends StartedHandshake[C] {
    val deposits: Exchange.Deposits
  }
}
