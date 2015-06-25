package coinffeine.peer.exchange.protocol

import coinffeine.model.bitcoin._
import coinffeine.model.exchange.DepositPendingExchange
import coinffeine.peer.exchange.protocol.Handshake.{InvalidRefundSignature, InvalidRefundTransaction}

/** Create a mock handshake with random transactions.
  *
  * @param exchange       Info about the exchange being mocked
  */
class MockHandshake(override val exchange: DepositPendingExchange)
  extends Handshake {

  override val myDeposit = dummyImmutableTransaction(1)
  override val myUnsignedRefund = dummyImmutableTransaction(2)
  val mySignedRefund = dummyImmutableTransaction(3)
  val counterpartCommitmentTransaction = dummyTransaction(4)
  val counterpartRefund = dummyTransaction(5)
  val invalidRefundTransaction = dummyTransaction(6)

  override def signHerRefund(txToSign: ImmutableTransaction) =
    if (txToSign.get == counterpartRefund) FakeExchangeProtocol.CounterpartRefundSignature
    else throw new InvalidRefundTransaction(txToSign, "Invalid refundSig")

  override def signMyRefund(sig: TransactionSignature) =
    if (sig == FakeExchangeProtocol.RefundSignature) mySignedRefund
    else throw new InvalidRefundSignature(myUnsignedRefund, sig)

  private def dummyImmutableTransaction(lockTime: Int) =
    ImmutableTransaction(dummyTransaction(lockTime))

  private def dummyTransaction(lockTime: Int): MutableTransaction = {
    val tx = new MutableTransaction(exchange.parameters.network)
    tx.setLockTime(lockTime)
    tx
  }
}
