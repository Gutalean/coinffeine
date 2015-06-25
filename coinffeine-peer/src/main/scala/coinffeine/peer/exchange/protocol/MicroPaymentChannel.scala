package coinffeine.peer.exchange.protocol

import scala.util.Try

import coinffeine.model.Both
import coinffeine.model.bitcoin.{ImmutableTransaction, TransactionSignature}
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.ActiveExchange._
import coinffeine.model.exchange.RunningExchange
import coinffeine.peer.exchange.protocol.MicroPaymentChannel._

trait MicroPaymentChannel {

  val exchange: RunningExchange

  val currentStep: Step

  def nextStep: MicroPaymentChannel

  /** Check signature validity for the current step.
    *
    * @param herSignatures  Counterpart signatures for buyer and seller deposits
    * @return               A success is everything is correct or a failure with an
    *                       [[InvalidSignaturesException]] otherwise
    */
  def validateCurrentTransactionSignatures(herSignatures: Both[TransactionSignature]): Try[Unit]

  def signCurrentTransaction: Both[TransactionSignature]

  /** Given valid counterpart signatures it generates the closing transaction.
    *
    * The resulting transaction contains the following funds:
    *
    *  * For the last transaction in the happy path scenario it contains both the exchanged
    *    amount for the buyer and the deposits for each participant.
    *  * For an intermediate step, just the confirmed steps amounts for the buyer and the
    *    rest of the amount to exchange for the seller. Note that deposits are lost as fees.
    *
    * @param herSignatures  Valid counterpart signatures
    * @return               Ready to broadcast transaction
    */
  @throws[InvalidSignaturesException]("if herSignatures are not valid")
  def closingTransaction(herSignatures: Both[TransactionSignature]): ImmutableTransaction
}

object MicroPaymentChannel {

  sealed trait Step {
    /** Step number in the range 1 to totalSteps */
    val value: Int

    val isFinal: Boolean

    /** Step after this one */
    @throws[IllegalArgumentException]("if this step is final")
    def next: Step

    def select(amounts: Amounts): StepAmounts
  }

  case class IntermediateStep(override val value: Int, breakdown: StepBreakdown) extends Step {
    require(value > 0, s"Step number must be positive ($value given)")
    require(value < breakdown.totalSteps,
      s"Step number must be less than ${breakdown.totalSteps} ($value given)")

    override val isFinal = false
    override def next =
      if (value == breakdown.intermediateSteps) FinalStep(breakdown) else copy(value = value + 1)
    override val toString = s"step $value/${breakdown.totalSteps}"

    override def select(amounts: Amounts): IntermediateStepAmounts =
      select(amounts.intermediateSteps)

    def select(
        amounts: Seq[IntermediateStepAmounts]): IntermediateStepAmounts =
      amounts(value - 1)
  }

  case class FinalStep(breakdown: StepBreakdown) extends Step {
    override val value = breakdown.totalSteps
    override val isFinal = true
    override def next = throw new IllegalArgumentException("Already at the last step")
    override def toString = s"step $value/$value"
    override def select(amounts: Amounts): FinalStepAmounts =
      amounts.finalStep
  }

  case class InvalidSignaturesException(signatures: Both[TransactionSignature], cause: Throwable = null)
    extends IllegalArgumentException(s"Invalid signatures $signatures", cause)
}
