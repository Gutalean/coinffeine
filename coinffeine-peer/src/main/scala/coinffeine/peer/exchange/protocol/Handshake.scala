package coinffeine.peer.exchange.protocol

import coinffeine.model.bitcoin.{ImmutableTransaction, TransactionSignature}
import coinffeine.model.currency.FiatCurrency
import coinffeine.peer.exchange.protocol.Handshake.{InvalidRefundSignature, InvalidRefundTransaction}

trait Handshake[C <: FiatCurrency] {

  val exchange: HandshakingExchange[C]

  /** Ready to be broadcast deposit */
  def myDeposit: ImmutableTransaction

  def myUnsignedRefund: ImmutableTransaction

  @throws[InvalidRefundSignature]
  def signMyRefund(herSignature: TransactionSignature): ImmutableTransaction

  @throws[InvalidRefundTransaction]("refund transaction was not valid (e.g. incorrect amount)")
  def signHerRefund(herRefund: ImmutableTransaction): TransactionSignature
}

object Handshake {

  case class InvalidRefundSignature(
      refundTx: ImmutableTransaction,
      invalidSignature: TransactionSignature) extends IllegalArgumentException(
    s"invalid signature $invalidSignature for refund transaction $refundTx")

  case class InvalidRefundTransaction(invalidTransaction: ImmutableTransaction, cause: String)
    extends IllegalArgumentException(s"invalid refund transaction: $invalidTransaction: $cause")
}
