package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.order.Price

trait ActiveExchange[C <: FiatCurrency] extends Exchange[C] {
  def metadata: ExchangeMetadata[C]
  def amounts: ActiveExchange.Amounts[C] = metadata.amounts
  def parameters: ActiveExchange.Parameters = metadata.parameters

  override def currency: C = metadata.amounts.currency
  override def id: ExchangeId = metadata.id
  override def role: Role = metadata.role
  override def counterpartId: PeerId = metadata.counterpartId

  override def exchangedBitcoin: Both[Bitcoin.Amount] = Both(
    buyer = amounts.netBitcoinExchanged,
    seller = amounts.grossBitcoinExchanged
  )
  override def exchangedFiat: Both[CurrencyAmount[C]] = Both(
    buyer = amounts.grossFiatExchanged,
    seller = amounts.netFiatExchanged
  )

  override def lockTime = parameters.lockTime
}

object ActiveExchange {

  /** Configurable parameters of an exchange.
    *
    * @param lockTime  The block number which will cause the refunds transactions to be valid
    * @param network   Bitcoin network
    */
  case class Parameters(lockTime: Long, network: Network)

  /** How the exchange is break down into steps */
  case class StepBreakdown(intermediateSteps: Int) {
    require(intermediateSteps > 0, s"Intermediate steps must be positive ($intermediateSteps given)")
    val totalSteps = intermediateSteps + 1
  }

  trait StepAmounts[C <: FiatCurrency] {
    val depositSplit: Both[Bitcoin.Amount]
    val progress: Exchange.Progress
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
      override val progress: Exchange.Progress) extends StepAmounts[C] {
    require(!depositSplit.forall(_.isNegative),
      s"deposit split amounts must be non-negative ($depositSplit given)")
    require(fiatAmount.isPositive, s"fiat amount must be positive ($fiatAmount given)")
  }

  case class FinalStepAmounts[C <: FiatCurrency](
                                                  override val depositSplit: Both[Bitcoin.Amount],
                                                  override val progress: Exchange.Progress) extends StepAmounts[C]

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

    /** Price is subjective because of the different fees supported by buyer and seller */
    def price(role: Role): Price[C] = role match {
      case BuyerRole => Price.whenExchanging(netBitcoinExchanged, grossFiatExchanged)
      case SellerRole => Price.whenExchanging(grossBitcoinExchanged, netFiatExchanged)
    }

    val bitcoinRequired = deposits.map(_.input)
    val fiatRequired = Both(buyer = grossFiatExchanged, seller = CurrencyAmount.zero(currency))

    val breakdown = StepBreakdown(intermediateSteps.length)

    def exchangedBitcoin: Both[Bitcoin.Amount] =
      Both(buyer = netBitcoinExchanged, seller = grossBitcoinExchanged)

    def exchangedFiat: Both[CurrencyAmount[C]] =
      Both(buyer = grossFiatExchanged, seller = netFiatExchanged)
  }

  type Deposits = Both[ImmutableTransaction]

  def create[C <: FiatCurrency](id: ExchangeId,
                                role: Role,
                                counterpartId: PeerId,
                                amounts: Amounts[C],
                                parameters: Parameters,
                                createdOn: DateTime) =
    HandshakingExchange(ExchangeMetadata(id, role, counterpartId, amounts, parameters, createdOn))

  sealed trait State[C <: FiatCurrency] {
    val progress: Exchange.Progress
    val isCompleted: Boolean
  }
}
