package coinffeine.peer.exchange.micropayment

import scala.util.Failure

import org.bitcoinj.crypto.TransactionSignature
import org.slf4j.LoggerFactory

import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.exchange.Both
import coinffeine.peer.exchange.protocol.MicroPaymentChannel
import coinffeine.peer.exchange.protocol.MicroPaymentChannel.{FinalStep, IntermediateStep, Step}
import coinffeine.peer.payment.PaymentProcessorActor
import coinffeine.protocol.messages.exchange.{PaymentProof, StepSignatures}

class BuyerChannel[C <: FiatCurrency](initialChannel: MicroPaymentChannel[C]) {
  private var channel = initialChannel
  private var _lastOffer: Option[ImmutableTransaction] = None
  private var _lastPaymentProof: Option[PaymentProof] = None

  def lastOffer: Option[ImmutableTransaction] = _lastOffer
  def lastPaymentProof: Option[PaymentProof] = _lastPaymentProof
  def currentStep: Step = channel.currentStep

  def shouldAcceptSignatures(stepSigs: StepSignatures): Boolean =
    belongToCurrentStep(stepSigs) && areValidSignatures(stepSigs.signatures)

  private def belongToCurrentStep(stepSigs: StepSignatures) =
    stepSigs.step == channel.currentStep.value

  private def areValidSignatures(signatures: Both[TransactionSignature]) =
    channel.validateCurrentTransactionSignatures(signatures) match {
      case Failure(cause) =>
        BuyerChannel.Log.error("Exchange {}: invalid signatures for step {}", channel.exchange.id,
          channel.currentStep, cause)
        false
      case _ => true
    }

  def acceptSignatures(signatures: Both[TransactionSignature]): Unit = {
    _lastOffer = Some(channel.closingTransaction(signatures))
  }

  def paymentRequest: Option[PaymentProcessorActor.Pay[C]] = channel.currentStep match {
    case _: FinalStep => None
    case step: IntermediateStep =>
      Some(PaymentProcessorActor.Pay(
        fundsId = channel.exchange.id,
        to = channel.exchange.state.counterpart.paymentProcessorAccount,
        amount = step.select(channel.exchange.amounts).fiatAmount,
        comment = PaymentDescription(channel.exchange.id, step)
      ))
  }

  def completePayment(paymentId: String): Unit = {
    _lastPaymentProof = Some(PaymentProof(channel.exchange.id, paymentId, channel.currentStep.value))
    channel = channel.nextStep
  }
}

private object BuyerChannel {
  val Log = LoggerFactory.getLogger(classOf[BuyerChannel[_]])
}
