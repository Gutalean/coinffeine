package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.DepositPendingExchange
import coinffeine.peer.exchange.protocol.Handshake.{InvalidRefundSignature, InvalidRefundTransaction}
import coinffeine.peer.exchange.protocol._

private[impl] class DefaultHandshake(
   override val exchange: DepositPendingExchange,
   override val myDeposit: ImmutableTransaction) extends Handshake {

  override val myUnsignedRefund: ImmutableTransaction = UnsignedRefundTransaction(
    deposit = myDeposit,
    outputKey = exchange.user.bitcoinKey,
    outputAmount = exchange.role.select(exchange.amounts.refunds),
    lockTime = exchange.parameters.lockTime,
    network = exchange.parameters.network
  )

  @throws[InvalidRefundTransaction]
  override def signHerRefund(herRefund: ImmutableTransaction) = signRefundTransaction(
    tx = herRefund.get,
    expectedAmount = exchange.role.counterpart.select(exchange.amounts.refunds)
  )

  @throws[InvalidRefundSignature]
  override def signMyRefund(herSignature: TransactionSignature) = {
    if (!TransactionProcessor.isValidSignature(
        myUnsignedRefund.get, index = 0, herSignature,
        signerKey = exchange.counterpart.bitcoinKey,
        exchange.requiredSignatures.toSeq)) {
      throw InvalidRefundSignature(myUnsignedRefund, herSignature)
    }
    ImmutableTransaction {
      val tx = myUnsignedRefund.get
      val mySignature = signRefundTransaction(
        tx,
        expectedAmount = exchange.role.select(exchange.amounts.refunds))
      val buyerSignature = exchange.role.buyer(mySignature, herSignature)
      val sellerSignature = exchange.role.seller(mySignature, herSignature)
      tx.getInput(0).setSignatures(buyerSignature, sellerSignature)
      tx
    }
  }

  private def signRefundTransaction(tx: MutableTransaction,
                                    expectedAmount: BitcoinAmount): TransactionSignature = {
    ensureValidRefundTransaction(ImmutableTransaction(tx), expectedAmount)
    tx.signMultisigOutput(
      index = 0,
      signAs = exchange.user.bitcoinKey,
      exchange.requiredSignatures.toSeq
    )
  }

  private def ensureValidRefundTransaction(tx: ImmutableTransaction,
                                           expectedAmount: BitcoinAmount) = {
    val validator = new RefundTransactionValidation(exchange.parameters, expectedAmount)
    validator(tx).swap.foreach { errors =>
      throw new InvalidRefundTransaction(tx, errors.list.mkString(", "))
    }
  }
}
