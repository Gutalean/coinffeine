package coinffeine.peer.exchange.protocol.impl

import scala.collection.JavaConverters._

import org.bitcoinj.core.Wallet.SendRequest

import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.model.exchange.SampleExchange

class TransactionProcessorTest extends BitcoinjTest with SampleExchange {

  val buyerKey = participants.buyer.bitcoinKey
  val sellerKey = participants.seller.bitcoinKey

  "Multisign transaction creation" should "fail if the amount to commit is less or equal to zero" in {
    val buyerWallet = createWallet(buyerKey, 5.BTC)
    val funds = TransactionProcessor.collectFunds(buyerWallet, 2.BTC).toSeq
    an [IllegalArgumentException] should be thrownBy {
      TransactionProcessor.createMultiSignedDeposit(
        funds, 0.BTC, buyerWallet.getChangeAddress, requiredSignatures, buyerWallet)
    }
  }

  it should "commit the correct amount when the input exceeds the amount needed" in {
    val buyerWallet = createWallet(buyerKey, 5.BTC)
    val commitmentAmount = 2 BTC
    val funds = TransactionProcessor.collectFunds(buyerWallet, 5.BTC).toSeq
    val transaction = TransactionProcessor.createMultiSignedDeposit(
      funds, commitmentAmount, buyerWallet.getChangeAddress, requiredSignatures, buyerWallet
    )
    Bitcoin.fromSatoshi(transaction.getValue(buyerWallet).value) shouldBe -commitmentAmount
  }

  it should "commit the correct amount when the input matches the amount needed" in {
    val commitmentAmount = 2 BTC
    val buyerWallet = createWallet(buyerKey, commitmentAmount)
    val funds = TransactionProcessor.collectFunds(buyerWallet, commitmentAmount).toSeq
    val transaction = TransactionProcessor.createMultiSignedDeposit(
      funds, commitmentAmount, buyerWallet.getChangeAddress, requiredSignatures, buyerWallet
    )
    Bitcoin.fromSatoshi(transaction.getValue(buyerWallet).value) shouldBe -commitmentAmount
  }

  it should "produce a TX ready for broadcast and insertion into the blockchain" in {
    val buyerWallet = createWallet(buyerKey, 2.BTC)
    val funds = TransactionProcessor.collectFunds(buyerWallet, 2.BTC).toSeq
    val multiSigDeposit = TransactionProcessor.createMultiSignedDeposit(
      funds, 2.BTC, buyerWallet.getChangeAddress, requiredSignatures, buyerWallet
    )
    sendToBlockChain(multiSigDeposit)
  }

  it should "spend a multisigned deposit" in {
    val buyerWallet = createWallet(buyerKey, 2.BTC)
    val sellerWallet = createWallet(sellerKey)

    val funds = TransactionProcessor.collectFunds(buyerWallet, 2.BTC).toSeq
    val multiSigDeposit = TransactionProcessor.createMultiSignedDeposit(
      funds, 2.BTC, buyerWallet.getChangeAddress, requiredSignatures, buyerWallet
    )
    sendToBlockChain(multiSigDeposit)

    val tx = TransactionProcessor.createUnsignedTransaction(
      Seq(multiSigDeposit.getOutput(0)), Seq(sellerKey -> 2.BTC), network
    )
    TransactionProcessor.setMultipleSignatures(tx, index = 0,
      TransactionProcessor.signMultiSignedOutput(tx, index = 0, buyerKey, requiredSignatures.toSeq),
      TransactionProcessor.signMultiSignedOutput(tx, index = 0, sellerKey, requiredSignatures.toSeq)
    )
    sendToBlockChain(tx)
    Bitcoin.fromSatoshi(sellerWallet.getBalance.value) shouldBe 2.BTC
  }


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
