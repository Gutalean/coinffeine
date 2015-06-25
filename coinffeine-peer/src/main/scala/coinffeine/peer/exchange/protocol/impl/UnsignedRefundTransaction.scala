package coinffeine.peer.exchange.protocol.impl

import coinffeine.model.bitcoin.{ImmutableTransaction, Network, PublicKey}
import coinffeine.model.currency.BitcoinAmount

private[impl] case class UnsignedRefundTransaction(
    deposit: ImmutableTransaction,
    outputKey: PublicKey,
    outputAmount: BitcoinAmount,
    lockTime: Long,
    network: Network) extends ImmutableTransaction(
  TransactionProcessor.createUnsignedTransaction(
    inputs = Seq(deposit.get.getOutput(0)),
    outputs = Seq(outputKey -> outputAmount),
    network = network,
    lockTime = Some(lockTime)
  ))
