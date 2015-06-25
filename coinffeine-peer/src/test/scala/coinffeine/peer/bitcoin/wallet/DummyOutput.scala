package coinffeine.peer.bitcoin.wallet

import scala.util.Random

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{MutableTransaction, MutableTransactionOutput, PublicKey}
import coinffeine.model.currency._

object DummyOutput {

  def of(amount: BitcoinAmount): MutableTransactionOutput = {
    val tx = new MutableTransaction(CoinffeineUnitTestNetwork)
    tx.setLockTime(Random.nextLong().abs)
    tx.addOutput(1.BTC, new PublicKey)
  }
}
