package coinffeine.peer.exchange.micropayment

import scala.util.Try

import coinffeine.model.currency.FiatCurrency
import coinffeine.model.payment.Payment
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep, Step}
import coinffeine.protocol.messages.exchange.{StepSignatures, PaymentProof}

class SellerChannel[C <: FiatCurrency](initialChannel: MicroPaymentChannel[C]) {
  private var channel = initialChannel

  def step: Step = channel.currentStep

  def validatePayment(payment: Payment[_ <: FiatCurrency]): Try[Unit] = Try {
    channel.currentStep match {
      case _: FinalStep => throw new IllegalArgumentException("No payment is expected at the final step")
      case step: IntermediateStep =>
        val participants = channel.exchange.participants
        require(payment.amount == step.select(channel.exchange.amounts).fiatAmount,
          s"Payment $step amount does not match expected amount")
        require(payment.receiverId == participants.seller.paymentProcessorAccount,
          s"Payment $step is not being sent to the seller")
        require(payment.senderId == participants.buyer.paymentProcessorAccount,
          s"Payment $step is not coming from the buyer")
        require(payment.description == PaymentDescription(channel.exchange.id, step),
          s"Payment $step does not have the required description")
        require(payment.completed, s"Payment $step is not complete")
    }
  }

  def relevantPaymentProof(proof: PaymentProof): Boolean =
    proof.exchangeId == channel.exchange.id && proof.step == channel.currentStep.value

  def stepSignatures: StepSignatures =
    StepSignatures(channel.exchange.id, step.value, channel.signCurrentTransaction)

  def stepPayed(): Unit = {
    channel = channel.nextStep
  }
}
