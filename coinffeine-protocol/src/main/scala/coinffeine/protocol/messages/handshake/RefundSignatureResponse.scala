package coinffeine.protocol.messages.handshake

import coinffeine.model.bitcoin.{TransactionSignature, TransactionSignatureUtils}
import coinffeine.model.exchange.ExchangeId
import coinffeine.protocol.messages.PublicMessage

case class RefundSignatureResponse(exchangeId: ExchangeId, refundSignature: TransactionSignature)
  extends PublicMessage {

  override def equals(that: Any) = that match {
    case rep: RefundSignatureResponse => (rep.exchangeId == exchangeId) &&
      TransactionSignatureUtils.equals(rep.refundSignature, refundSignature)
    case _ => false
  }

  override def hashCode(): Int =
    31 * exchangeId.hashCode() + TransactionSignatureUtils.hashCode(refundSignature)

  override def toString =
    s"RefundSignatureResponse($exchangeId, ${TransactionSignatureUtils.toString(refundSignature)})"
}
