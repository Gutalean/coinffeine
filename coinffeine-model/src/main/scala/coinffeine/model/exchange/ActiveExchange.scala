package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.order.Price

trait ActiveExchange extends Exchange {
  def metadata: ExchangeMetadata
  def amounts: ActiveExchange.Amounts = metadata.amounts
  def parameters: ActiveExchange.Parameters = metadata.parameters

  override def currency: FiatCurrency = metadata.amounts.currency
  override def id: ExchangeId = metadata.id
  override def role: Role = metadata.role
  override def counterpartId: PeerId = metadata.counterpartId

  override def exchangedBitcoin: Both[BitcoinAmount] = Both(
    buyer = amounts.netBitcoinExchanged,
    seller = amounts.grossBitcoinExchanged
  )
  override def exchangedFiat: Both[FiatAmount] = Both(
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

  trait StepAmounts {
    val depositSplit: Both[BitcoinAmount]
    val progress: Exchange.Progress
  }

  /** Amounts involved on one exchange step.
    *
    * @param depositSplit   How to distribute funds on this step
    * @param fiatAmount     Net fiat amount to change hands on this step
    * @param fiatFee        Fiat fees to be payed on this step
    */
  case class IntermediateStepAmounts(
      override val depositSplit: Both[BitcoinAmount],
      fiatAmount: FiatAmount,
      fiatFee: FiatAmount,
      override val progress: Exchange.Progress) extends StepAmounts {
    require(!depositSplit.forall(_.isNegative),
      s"deposit split amounts must be non-negative ($depositSplit given)")
    require(fiatAmount.isPositive, s"fiat amount must be positive ($fiatAmount given)")
  }

  case class FinalStepAmounts(
    override val depositSplit: Both[BitcoinAmount],
    override val progress: Exchange.Progress) extends StepAmounts

  /** Characterizes the amounts to be deposited by a part. The difference between input and
    * output is the fee.
    */
  case class DepositAmounts(input: BitcoinAmount, output: BitcoinAmount) {
    require(input.isPositive, "Cannot spent a negative or zero amount")
    require(output.isPositive, "Should not deposit non-positive amount")
    require(input >= output, "Deposits should have a greater or equal input versus output")

    def fee: BitcoinAmount = input - output
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
    */
  case class Amounts(
      grossBitcoinExchanged: BitcoinAmount,
      grossFiatExchanged: FiatAmount,
      deposits: Both[DepositAmounts],
      refunds: Both[BitcoinAmount],
      intermediateSteps: Seq[IntermediateStepAmounts],
      finalStep: FinalStepAmounts) {
    require(grossBitcoinExchanged.isPositive,
      s"Cannot exchange a gross amount of $grossBitcoinExchanged")
    require(grossFiatExchanged.isPositive, s"Cannot exchange a gross amount of $grossFiatExchanged")
    require(intermediateSteps.nonEmpty, "There should be at least one step")

    val currency: FiatCurrency = grossFiatExchanged.currency

    /** Net amount of bitcoins to be exchanged */
    val netBitcoinExchanged: BitcoinAmount = finalStep.depositSplit.buyer - deposits.buyer.input
    require(netBitcoinExchanged.isPositive, s"Cannot exchange a net amount of $netBitcoinExchanged")

    /** Net amount of fiat to be exchanged */
    val netFiatExchanged: FiatAmount = currency.sum(intermediateSteps.map(_.fiatAmount))
    require(netFiatExchanged <= grossFiatExchanged)
    require(netFiatExchanged.isPositive, s"Cannot exchange a net amount of $netFiatExchanged")

    val steps: Seq[StepAmounts] = intermediateSteps :+ finalStep

    /** Price is subjective because of the different fees supported by buyer and seller */
    def price(role: Role): Price = role match {
      case BuyerRole => Price.whenExchanging(netBitcoinExchanged, grossFiatExchanged)
      case SellerRole => Price.whenExchanging(grossBitcoinExchanged, netFiatExchanged)
    }

    val bitcoinRequired = deposits.map(_.input)
    val fiatRequired = Both(buyer = grossFiatExchanged, seller = currency.zero)

    val breakdown = StepBreakdown(intermediateSteps.length)

    def exchangedBitcoin: Both[BitcoinAmount] =
      Both(buyer = netBitcoinExchanged, seller = grossBitcoinExchanged)

    def exchangedFiat: Both[FiatAmount] =
      Both(buyer = grossFiatExchanged, seller = netFiatExchanged)
  }

  type Deposits = Both[ImmutableTransaction]

  def create(
      id: ExchangeId,
      role: Role,
      counterpartId: PeerId,
      amounts: Amounts,
      parameters: Parameters,
      createdOn: DateTime) =
    HandshakingExchange(ExchangeMetadata(id, role, counterpartId, amounts, parameters, createdOn))

  sealed trait State {
    val progress: Exchange.Progress
    val isCompleted: Boolean
  }
}
