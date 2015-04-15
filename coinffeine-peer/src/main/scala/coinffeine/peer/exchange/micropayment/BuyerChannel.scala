package coinffeine.peer.exchange.micropayment

import scala.util.Failure

import com.typesafe.scalalogging.LazyLogging
import org.bitcoinj.crypto.TransactionSignature

import coinffeine.model.Both
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep, Step}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

class BuyerChannel[C <: FiatCurrency](initialChannel: MicroPaymentChannel[C]) extends LazyLogging {
  private var channel = initialChannel
  private var _lastOffer: Option[ImmutableTransaction] = None
  private var _lastPaymentProof: Option[PaymentProof] = None
  private var _lastCompletedStep: Option[Step] = None

  def lastOffer: Option[ImmutableTransaction] = _lastOffer
  def lastPaymentProof: Option[PaymentProof] = _lastPaymentProof
  def lastCompletedStep: Option[Step] = _lastCompletedStep
  def currentStep: Step = channel.currentStep

  def shouldAcceptSignatures(stepSigs: StepSignatures): Boolean =
    belongToCurrentStep(stepSigs) && areValidSignatures(stepSigs.signatures)

  private def belongToCurrentStep(stepSigs: StepSignatures) =
    stepSigs.step == channel.currentStep.value

  private def areValidSignatures(signatures: Both[TransactionSignature]) =
    channel.validateCurrentTransactionSignatures(signatures) match {
      case Failure(cause) =>
        logger.error(
          s"Exchange ${channel.exchange.id}: invalid signatures for step ${channel.currentStep}",
          cause)
        false
      case _ => true
    }

  def acceptSignatures(signatures: Both[TransactionSignature]): Unit = {
    _lastOffer = Some(channel.closingTransaction(signatures))
    _lastCompletedStep = Some(channel.currentStep)
  }

  def paymentRequest: Option[PaymentProcessorActor.Pay[C]] = channel.currentStep match {
    case _: FinalStep => None
    case step: IntermediateStep =>
      Some(PaymentProcessorActor.Pay(
        fundsId = channel.exchange.id,
        to = channel.exchange.counterpart.paymentProcessorAccount,
        amount = step.select(channel.exchange.amounts).fiatAmount,
        comment = PaymentDescription(channel.exchange.id, step)
      ))
  }

  def completePayment(paymentId: String): Unit = {
    _lastPaymentProof = Some(PaymentProof(channel.exchange.id, paymentId, channel.currentStep.value))
    channel = channel.nextStep
  }
}
