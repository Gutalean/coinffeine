package coinffeine.peer.bitcoin.wallet

import org.bitcoinj.core.TransactionOutPoint

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{Hash, KeyPair, MutableTransaction}
import coinffeine.model.currency._
import coinffeine.peer.bitcoin.wallet.SmartWallet.NotEnoughFunds

class SmartWalletTest extends UnitTest with BitcoinjTest {

  "Smart wallet" must "create a new transaction" in new Fixture {
    val tx = wallet.createTransaction(1.BTC, someAddress)
    Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate).value) shouldBe (-1.BTC)
  }

  it must "create a new transaction with chosen inputs" in new Fixture {
    withFees {
      val inputs = wallet.spendCandidates.take(1).map(_.getOutPointFor).toSet
      val amount = 1.BTC
      val tx = wallet.createTransaction(inputs, amount, someAddress)
      val amountPlusFee = amount + MutableTransaction.ReferenceDefaultMinTxFee
      Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate).value) shouldBe -amountPlusFee
    }
  }

  it must "fail to create a new transaction when insufficient balance" in new Fixture {
    a [NotEnoughFunds] shouldBe thrownBy {
      wallet.createTransaction(20.BTC, someAddress)
    }
  }

  it must "create a multisign transaction" in new Fixture {
    val tx = wallet.createMultisignTransaction(signatures, 1.BTC, 0.1.BTC)
    wallet.value(tx.get) shouldBe (-1.1.BTC)
    Bitcoin.fromSatoshi(tx.get.getOutput(0).getValue.value) shouldBe 1.BTC
  }

  it must "fail to create a deposit when there is no enough amount" in new Fixture {
    a [NotEnoughFunds] shouldBe thrownBy {
      wallet.createMultisignTransaction(signatures, 10000.BTC, 0.BTC)
    }
  }

  it must "fail to create a deposit when available outputs are unconfirmed" in new Fixture {
    wallet.createTransaction(1.BTC, wallet.currentReceiveAddress)
    a [NotEnoughFunds] shouldBe thrownBy {
      wallet.createMultisignTransaction(signatures, 1.BTC, 0.BTC)
    }
  }

  it must "release unpublished deposit funds" in new Fixture {
    val tx = wallet.createMultisignTransaction(signatures, 1.BTC, 0.1.BTC)
    wallet.releaseTransaction(tx)
    wallet.estimatedBalance shouldBe initialFunds
  }

  it must "not find the spending transaction of unknown outputs" in new Fixture {
    val unknownOutput = new TransactionOutPoint(wallet.delegate.getParams, 0,
      new Hash("b7008522a94c2ee3c1f4612eec33e5483ed35ea1fb1ea52237cc7ae2f64d232e"))
    wallet.findTransactionSpendingOutput(unknownOutput) shouldBe 'empty
  }

  it must "not find the spending transaction of unspent outputs" in new Fixture {
    val unspentOutput = wallet.spendCandidates.head.getOutPointFor
    wallet.findTransactionSpendingOutput(unspentOutput) shouldBe 'empty
  }

  it must "find the spending transaction of a spent output" in new Fixture {
    val transaction = wallet.createTransaction(1.BTC, someAddress)
    val spentOutput = transaction.get.getInput(0).getOutpoint
    wallet.findTransactionSpendingOutput(spentOutput) shouldBe Some(transaction)
  }

  trait Fixture {
    val initialFunds = 10.BTC
    val someAddress = new KeyPair().toAddress(network)
    val wallet = new SmartWallet(createWallet(initialFunds))
    val signatures = Both(wallet.delegate.freshReceiveKey(), new KeyPair)
  }
}
