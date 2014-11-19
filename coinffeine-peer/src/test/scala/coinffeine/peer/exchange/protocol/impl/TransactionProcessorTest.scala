package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConverters._

import org.bitcoinj.core.Wallet.SendRequest

import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.model.exchange.SampleExchange

class TransactionProcessorTest extends BitcoinjTest with SampleExchange {

  val buyerKey = participants.buyer.bitcoinKey
  val sellerKey = participants.seller.bitcoinKey

  "Unsigned transaction creation" should "create valid transactions except for the signature" in {
    val buyerWallet = createWallet(buyerKey, 1.BTC)
    val sellerWallet = createWallet(sellerKey)
    val transaction = TransactionProcessor.createUnsignedTransaction(
      inputs = buyerWallet.calculateAllSpendCandidates(true).asScala,
      outputs = Seq(sellerKey -> 0.8.BTC, buyerKey -> 0.2.BTC),
      network = network
    )
    buyerWallet.signTransaction(SendRequest.forTx(transaction))
    sendToBlockChain(transaction)
    Bitcoin.fromSatoshi(buyerWallet.getBalance.value) shouldBe 0.2.BTC
    Bitcoin.fromSatoshi(sellerWallet.getBalance.value) shouldBe 0.8.BTC
  }
}
