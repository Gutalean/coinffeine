package coinffeine.model.exchange

import scala.util.Try

import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.market.Price
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

  val currency: C = amounts.netFiatExchanged.currency

  val progress: Exchange.Progress = state.progress
  require(progress.bitcoinsTransferred.buyer <= amounts.exchangedBitcoin.buyer &&
    progress.bitcoinsTransferred.seller <= amounts.exchangedBitcoin.seller,
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

  trait StepAmounts[C <: FiatCurrency] {
    val depositSplit: Both[Bitcoin.Amount]
    val progress: Progress
  }

  /** Amounts involved on one exchange step.
    *
    * @param depositSplit   How to distribute funds on this step
    * @param fiatAmount     Net fiat amount to change hands on this step
    * @param fiatFee        Fiat fees to be payed on this step
    */
  case class IntermediateStepAmounts[C <: FiatCurrency](
      override val depositSplit: Both[Bitcoin.Amount],
      fiatAmount: CurrencyAmount[C],
      fiatFee: CurrencyAmount[C],
      override val progress: Progress) extends StepAmounts[C] {
    require(!depositSplit.forall(_.isNegative),
      s"deposit split amounts must be non-negative ($depositSplit given)")
    require(fiatAmount.isPositive, s"fiat amount must be positive ($fiatAmount given)")
  }

  case class FinalStepAmounts[C <: FiatCurrency](
      override val depositSplit: Both[Bitcoin.Amount],
      override val progress: Progress) extends StepAmounts[C]

  /** Characterizes the amounts to be deposited by a part. The difference between input and
    * output is the fee.
    */
  case class DepositAmounts(input: Bitcoin.Amount, output: Bitcoin.Amount) {
    require(input.isPositive, "Cannot spent a negative or zero amount")
    require(output.isPositive, "Should not deposit non-positive amount")
    require(input >= output, "Deposits should have a greater or equal input versus output")

    def fee: Bitcoin.Amount = input - output
  }

  /** Amounts of money involved on an exchange.
    *
    * @param grossBitcoinExchanged  Overall amount of bitcoins to be exchanged (counterpart will
    *                               receive less due to fees)
    * @param grossFiatExchanged     Overall amount of fiat to be exchanged (counterpart will
    *                               receive less due to fees)
    * @param deposits               Exact amounts of bitcoins used on the deposit transactions,
    *                               considering fees
    * @param refunds                Amount refundable by each part after a lock time
    * @param intermediateSteps      Per-step exchanged amounts
    * @param finalStep              Final step amounts
    * @tparam C                     Fiat currency exchanged
    */
  case class Amounts[C <: FiatCurrency](grossBitcoinExchanged: Bitcoin.Amount,
                                        grossFiatExchanged: CurrencyAmount[C],
                                        deposits: Both[DepositAmounts],
                                        refunds: Both[Bitcoin.Amount],
                                        intermediateSteps: Seq[IntermediateStepAmounts[C]],
                                        finalStep: FinalStepAmounts[C]) {
    require(grossBitcoinExchanged.isPositive,
      s"Cannot exchange a gross amount of $grossBitcoinExchanged")
    require(grossFiatExchanged.isPositive, s"Cannot exchange a gross amount of $grossFiatExchanged")
    require(intermediateSteps.nonEmpty, "There should be at least one step")

    private implicit val num = grossFiatExchanged.numeric

    val currency: C = grossFiatExchanged.currency

    /** Net amount of bitcoins to be exchanged */
    val netBitcoinExchanged: Bitcoin.Amount = finalStep.depositSplit.buyer - deposits.buyer.input
    require(netBitcoinExchanged.isPositive, s"Cannot exchange a net amount of $netBitcoinExchanged")

    /** Net amount of fiat to be exchanged */
    val netFiatExchanged: CurrencyAmount[C] = intermediateSteps.map(_.fiatAmount).sum
    require(netFiatExchanged <= grossFiatExchanged)
    require(netFiatExchanged.isPositive, s"Cannot exchange a net amount of $netFiatExchanged")

    val steps: Seq[StepAmounts[C]] = intermediateSteps :+ finalStep

    val price: Price[C] = Price.whenExchanging(netBitcoinExchanged, netFiatExchanged)

    val bitcoinRequired = deposits.map(_.input)
    val fiatRequired = Both(buyer = grossFiatExchanged, seller = CurrencyAmount.zero(currency))

    val breakdown = Exchange.StepBreakdown(intermediateSteps.length)

    def exchangedBitcoin: Both[Bitcoin.Amount] =
      Both(buyer = netBitcoinExchanged, seller = grossBitcoinExchanged)

    def exchangedFiat: Both[CurrencyAmount[C]] =
      Both(buyer = grossFiatExchanged, seller = netFiatExchanged)
  }

  /** Funds reserved for the order this exchange belongs to */
  case class BlockedFunds(fiat: Option[PaymentProcessor.BlockedFundsId], bitcoin: BlockedCoinsId)

  type Deposits = Both[ImmutableTransaction]

  case class Progress(bitcoinsTransferred: Both[Bitcoin.Amount]) {
    def +(other: Progress) =
      Progress(bitcoinsTransferred.zip(other.bitcoinsTransferred).map { case (l, r) => l + r })
  }

  def noProgress[C <: FiatCurrency](c: C) = Exchange.Progress(Both.fill(Bitcoin.Zero))

  def notStarted[C <: FiatCurrency](id: ExchangeId,
                                    role: Role,
                                    counterpartId: PeerId,
                                    amounts: Exchange.Amounts[C],
                                    parameters: Exchange.Parameters,
                                    blockedFunds: Exchange.BlockedFunds) = Exchange(
    id, role, counterpartId, amounts, parameters, blockedFunds, NotStarted()(amounts.currency))

  sealed trait State[C <: FiatCurrency] {
    val progress: Exchange.Progress
    val isCompleted: Boolean
  }

  case class NotStarted[C <: FiatCurrency]()(val currency: C) extends State[C] {
    override val toString = "not started"
    override val progress = Exchange.noProgress(currency)
    override val isCompleted = false
  }

  implicit class NonStartedTransitions[C <: FiatCurrency](val exchange: Exchange[C, NotStarted[C]])
    extends AnyVal {

    def startHandshaking(user: Exchange.PeerInfo,
                         counterpart: Exchange.PeerInfo): Exchange[C, Handshaking[C]] =
      exchange.copy(state = Handshaking(user, counterpart)(exchange.currency))

    def cancel(cause: CancellationCause,
               user: Option[Exchange.PeerInfo] = None): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(Cancellation(cause), exchange.state.progress, user,
        transaction = None))

    def abort(cause: AbortionCause,
              user: Exchange.PeerInfo,
              refundTx: ImmutableTransaction): Exchange[C, Aborting[C]] =
      exchange.copy(state = Aborting(cause, user, refundTx)(exchange.currency))
  }

  case class Handshaking[C <: FiatCurrency](user: Exchange.PeerInfo, counterpart: Exchange.PeerInfo)
                                           (val currency: C) extends State[C] with StartedHandshake[C] {
    override val toString = "handshaking"
    override val progress = Exchange.noProgress(currency)
    override val isCompleted = false
  }

  implicit class HandshakingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Handshaking[C]])
    extends AnyVal {

    def startExchanging(deposits: Exchange.Deposits): Exchange[C, Exchanging[C]] =
      exchange.copy(state = Exchanging(exchange.currency, exchange.state, deposits))

    def cancel(cause: CancellationCause): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(Cancellation(cause), exchange.state, transaction = None))

    def abort(cause: AbortionCause, refundTx: ImmutableTransaction): Exchange[C, Aborting[C]] =
      exchange.copy(state = Aborting(cause, exchange.state, refundTx))
  }

  case class Exchanging[C <: FiatCurrency](
      user: Exchange.PeerInfo,
      counterpart: Exchange.PeerInfo,
      deposits: Exchange.Deposits,
      progress: Exchange.Progress) extends State[C] with StartedExchange[C] {
    override val toString = "exchanging"
    override val isCompleted = false
  }

  object Exchanging {
    def apply[C <: FiatCurrency](currency: C,
                                 previousState: Handshaking[C],
                                 deposits: Exchange.Deposits): Exchanging[C] =
      Exchanging(previousState.user, previousState.counterpart, deposits,
        Exchange.noProgress(currency))
  }

  implicit class ExchangingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Exchanging[C]])
    extends AnyVal {

    def complete: Exchange[C, Successful[C]] =
      exchange.copy(state = Successful(exchange.amounts, exchange.state))

    def panicked(transaction: ImmutableTransaction): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(PanicBlockReached, exchange.state, Some(transaction)))

    def stepFailure(step: Int,
                    cause: Throwable,
                    transaction: Option[ImmutableTransaction]): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(StepFailed(step, cause), exchange.state, transaction))

    def unexpectedBroadcast(actualTransaction: ImmutableTransaction): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(UnexpectedBroadcast, exchange.state, Some(actualTransaction)))

    def noBroadcast: Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(NoBroadcast, exchange.state, transaction = None))

    def increaseProgress(increase: Both[Bitcoin.Amount]): Exchange[C, Exchanging[C]] = {
      val progress = exchange.state.progress + Exchange.Progress(increase)
      exchange.copy(state = exchange.state.copy(progress = progress))
    }
  }

  case class Aborting[C <: FiatCurrency](
      cause: AbortionCause,
      user: Exchange.PeerInfo,
      refundTx: ImmutableTransaction)(currency: C) extends State[C] {
    override val toString = s"aborting ($cause)"
    override val progress = Exchange.noProgress(currency)
    override val isCompleted = false
  }

  object Aborting {
    def apply[C <: FiatCurrency](cause: AbortionCause,
                                 previousState: Handshaking[C],
                                 refundTx: ImmutableTransaction): Aborting[C] =
      Aborting(cause, previousState.user, refundTx)(previousState.currency)
  }

  implicit class AbortingTransitions[C <: FiatCurrency](val exchange: Exchange[C, Aborting[C]])
    extends AnyVal {

    def broadcast(transaction: ImmutableTransaction): Exchange[C, Failed[C]] =
      exchange.copy(state = Failed(Abortion(exchange.state.cause), exchange.progress,
        Some(exchange.state.user), Some(transaction)))

    def failedToBroadcast: Exchange[C, Failed[C]] = exchange.copy(state = Failed(
      Abortion(exchange.state.cause),
      exchange.state.progress,
      Some(exchange.state.user),
      transaction = None
    ))
  }

  trait Completed[C <: FiatCurrency] extends State[C] {
    def isSuccess: Boolean
    override val isCompleted = true
  }

  sealed trait AbortionCause

  case class HandshakeWithCommitmentFailed(cause: Throwable) extends AbortionCause {
    override val toString = "aborted by failed handshake commitment"
  }

  case class InvalidCommitments(validation: Both[Try[Unit]]) extends AbortionCause {
    require(validation.toSeq.count(_.isFailure) > 0)
    override val toString = "aborted by invalid commitments"
  }

  sealed trait CancellationCause

  case object UserCancellation extends CancellationCause {
    override val toString = "cancelled by user"
  }

  case class CannotStartHandshake(cause: Throwable) extends CancellationCause {
    override val toString = "cancelled by handshake start issues"
  }

  sealed trait FailureCause

  case class Cancellation(cause: CancellationCause) extends FailureCause {
    override val toString = cause.toString
  }

  case class Abortion(cause: AbortionCause) extends FailureCause {
    override val toString = cause.toString
  }

  case object PanicBlockReached extends FailureCause {
    override val toString = "panic blocked reached"
  }

  case class StepFailed(step: Int, cause: Throwable) extends FailureCause {
    override val toString = s"step $step failed"
  }

  case object UnexpectedBroadcast extends FailureCause {
    override val toString = "unexpected transaction broadcast"
  }

  case object NoBroadcast extends FailureCause {
    override val toString = "missing transaction broadcast"
  }

  case class HandshakeFailed(cause: Throwable) extends FailureCause with CancellationCause {
    override val toString = "handshake failed"
  }

  case class Failed[C <: FiatCurrency](cause: FailureCause,
                                       progress: Progress,
                                       user: Option[Exchange.PeerInfo],
                                       transaction: Option[ImmutableTransaction])
    extends Completed[C] {
    override val toString = s"failed ($cause)"
    override val isSuccess = false
  }
  object Failed {
    def apply[C <: FiatCurrency](cause: FailureCause,
                                 previousState: StartedHandshake[C],
                                 transaction: Option[ImmutableTransaction]): Failed[C] =
      Failed(cause, previousState.progress, Some(previousState.user), transaction)
  }

  case class Successful[C <: FiatCurrency](user: Exchange.PeerInfo,
                                           counterpart: Exchange.PeerInfo,
                                           deposits: Exchange.Deposits)(amounts: Exchange.Amounts[C])
    extends Completed[C] with StartedExchange[C] {
    override val toString = "successful"
    override val progress = Progress(amounts.exchangedBitcoin)
    override val isSuccess = true
  }

  object Successful {
    def apply[C <: FiatCurrency](amounts: Exchange.Amounts[C],
                                 previousState: Exchanging[C]): Successful[C] =
      Successful(previousState.user, previousState.counterpart, previousState.deposits)(amounts)
  }

  trait StartedHandshake[C <: FiatCurrency] extends State[C] {
    val user: Exchange.PeerInfo
    val counterpart: Exchange.PeerInfo

    require(user.bitcoinKey.canSign)
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
