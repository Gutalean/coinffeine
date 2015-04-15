package coinffeine.protocol.messages.exchange

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

/** This message contains the seller's signatures for a step in a specific exchange
  * @param exchangeId The exchange id for which the signatures are valid
  * @param step The step number for which the signatures are valid
  * @param signatures The signatures for buyer and seller inputs for the step
  */
case class StepSignatures(exchangeId: ExchangeId, step: Int, signatures: Both[TransactionSignature])
  extends PublicMessage {

  override def equals(other: Any) = other match {
    case that: StepSignatures =>
      (that.exchangeId == exchangeId) && (that.step == step) &&
      TransactionSignatureUtils.equals(
        that.signatures.buyer, signatures.buyer) &&
      TransactionSignatureUtils.equals(
        that.signatures.seller, signatures.seller)
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(exchangeId.hashCode(), step.hashCode()) ++
      signatures.toSeq.map(TransactionSignatureUtils.hashCode)
    state.foldLeft(0)((a, b) => 31 * a + b)
  }

  override def toString = "StepSignatures(%s, step = %d, buyerSig = %s, sellerSig = %s)"
    .format(exchangeId, step, TransactionSignatureUtils.toString(signatures.buyer),
      TransactionSignatureUtils.toString(signatures.seller))
}
