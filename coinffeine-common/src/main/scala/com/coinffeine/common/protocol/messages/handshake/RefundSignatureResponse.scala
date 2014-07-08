package com.coinffeine.common.protocol.messages.handshake

import com.coinffeine.common.bitcoin.{TransactionSignatureUtils, TransactionSignature}
import com.coinffeine.common.exchange.Exchange
import com.coinffeine.common.protocol.messages.PublicMessage

case class RefundSignatureResponse(exchangeId: Exchange.Id, refundSignature: TransactionSignature)
  extends PublicMessage {

  override def equals(that: Any) = that match {
    case rep: RefundSignatureResponse => (rep.exchangeId == exchangeId) &&
      TransactionSignatureUtils.equals(rep.refundSignature, refundSignature)
    case _ => false
  }

  override def hashCode(): Int =
    31 * exchangeId.hashCode() + TransactionSignatureUtils.hashCode(refundSignature)
}
