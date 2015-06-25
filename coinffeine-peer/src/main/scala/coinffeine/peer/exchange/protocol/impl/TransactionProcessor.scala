package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConversions._

import org.bitcoinj.core.Transaction.SigHash
import org.bitcoinj.script.ScriptBuilder

import coinffeine.model.bitcoin._
import coinffeine.model.currency._

/** This trait encapsulates the transaction processing actions. */
object TransactionProcessor {

  def createUnsignedTransaction(inputs: Seq[MutableTransactionOutput],
                                outputs: Seq[(PublicKey, BitcoinAmount)],
                                network: Network,
                                lockTime: Option[Long] = None): MutableTransaction = {
    val tx = new MutableTransaction(network)
    lockTime.foreach(tx.setLockTime)
    for (input <- inputs) { tx.addInput(input).setSequenceNumber(0) }
    for ((pubKey, amount) <- outputs) {
      tx.addOutput(amount, pubKey.toAddress(network))
    }
    tx
  }

  def isValidSignature(transaction: MutableTransaction,
                       index: Int,
                       signature: TransactionSignature,
                       signerKey: KeyPair,
                       requiredSignatures: Seq[PublicKey]): Boolean = {
    val script = ScriptBuilder.createMultiSigOutputScript(requiredSignatures.size, requiredSignatures)
    val hash = transaction.hashForSignature(index, script, SigHash.ALL, false)
    signerKey.verify(hash, signature)
  }
}
