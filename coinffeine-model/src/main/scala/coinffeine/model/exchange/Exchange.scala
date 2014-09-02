package coinffeine.model.exchange

import coinffeine.model.bitcoin._
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.{CurrencyAmount, BitcoinAmount, FiatCurrency}
import coinffeine.model.network.PeerId
import coinffeine.model.payment.PaymentProcessor

case class Exchange[C <: FiatCurrency, +S <: Exchange.State[C]](
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
  case class StepAmounts[C <: FiatCurrency](bitcoinAmount: BitcoinAmount,
                                            fiatAmount: CurrencyAmount[C],
                                            fiatFee: CurrencyAmount[C]) {
    require(bitcoinAmount.isPositive, s"bitcoin amount must be positive ($bitcoinAmount given)")
    require(fiatAmount.isPositive, s"fiat amount must be positive ($fiatAmount given)")

    def +(other: StepAmounts[C]) = StepAmounts(
      bitcoinAmount + other.bitcoinAmount,
      fiatAmount + other.fiatAmount,
      fiatFee + other.fiatFee
    )
  }

  /** Characterizes the amounts to be deposited by a part. The difference between input and
    * output is the fee.
    */
  case class DepositAmounts(input: BitcoinAmount, output: BitcoinAmount) {
    require(input.isPositive, "Cannot spent a negative or zero amount")
    require(output.isPositive, "Should not deposit non-positive amount")
    require(input >= output, "Deposits should have a greater or equal input versus output")
  }

  /** Amounts of money involved on an exchange.
    *
    * @param deposits          Net bitcoin amount deposited in multisign by each part
    * @param refunds           Amount refundable by each part after a lock time
    * @param steps             Per-step exchanged amounts
    * @tparam C                Fiat currency defined to this exchange
    */
  case class Amounts[C <: FiatCurrency](deposits: Both[BitcoinAmount],
                                        refunds: Both[BitcoinAmount],
                                        steps: Seq[StepAmounts[C]],
                                        transactionFee: BitcoinAmount = Bitcoin.Zero) {
    require(steps.nonEmpty, "There should be at least one step")
    val currency: C = steps.head.fiatAmount.currency

    /** Amount of bitcoins to be exchanged */
    val bitcoinExchanged: BitcoinAmount = steps.foldLeft(Bitcoin.Zero)(_ + _.bitcoinAmount)
    /** Amount of fiat to be exchanged */
    val fiatExchanged: CurrencyAmount[C] =
      steps.foldLeft[CurrencyAmount[C]](CurrencyAmount.zero(currency))(_ + _.fiatAmount)
    val price: CurrencyAmount[C] = fiatExchanged / bitcoinExchanged.value

    val fiatRequired = Both[CurrencyAmount[C]](
      buyer = fiatExchanged + steps.foldLeft[CurrencyAmount[C]](CurrencyAmount.zero(currency))(_ + _.fiatFee),
      seller = CurrencyAmount.zero(currency)
    )

    val depositTransactionAmounts = deposits.map(netAmount => DepositAmounts(
      input = netAmount + transactionFee * 1.5,
      output = netAmount + transactionFee / 2
    ))
    val bitcoinRequired = depositTransactionAmounts.map(_.input)

    val breakdown = Exchange.StepBreakdown(steps.length)
  }

  /** Funds reserved for the order this exchange belongs to */
  case class BlockedFunds(fiat: Option[PaymentProcessor.BlockedFundsId], bitcoin: BlockedCoinsId)

  type Deposits = Both[ImmutableTransaction]

  case class Progress[C <: FiatCurrency](bitcoinsTransferred: BitcoinAmount,
                                         fiatTransferred: CurrencyAmount[C]) {

    def +(other: Progress[C]) = Progress(
      bitcoinsTransferred = bitcoinsTransferred + other.bitcoinsTransferred,
      fiatTransferred = fiatTransferred + other.fiatTransferred
    )

    override def toString = s"progressed $bitcoinsTransferred by $fiatTransferred"
  }

  def noProgress[C <: FiatCurrency](c: C) = Exchange.Progress(Bitcoin.Zero, CurrencyAmount.zero(c))

  def notStarted[C <: FiatCurrency](id: ExchangeId,
                                    role: Role,
                                    counterpartId: PeerId,
                                    amounts: Exchange.Amounts[C],
                                    parameters: Exchange.Parameters,
                                    blockedFunds: Exchange.BlockedFunds) = Exchange(
    id, role, counterpartId, amounts, parameters, blockedFunds, NotStarted()(amounts.currency))

  sealed trait State[C <: FiatCurrency] {
    val progress: Exchange.Progress[C]
  }

  case class NotStarted[C <: FiatCurrency]()(val currency: C) extends State[C] {
    override val progress = Exchange.noProgress(currency)
  }

  implicit class NonStartedTransitions[C <: FiatCurrency](val exchange: Exchange[C, NotStarted[C]])
    extends AnyVal {

    def startHandshaking(user: Exchange.PeerInfo,
                         counterpart: Exchange.PeerInfo): Exchange[C, Handshaking[C]] =
      exchange.copy(state = Handshaking(user, counterpart)(exchange.currency))
  }

  case class Handshaking[C <: FiatCurrency](user: Exchange.PeerInfo, counterpart: Exchange.PeerInfo)
                                           (val currency: C) extends State[C] with StartedHandshake[C] {
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
        Exchange.noProgress[C](currency))
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
