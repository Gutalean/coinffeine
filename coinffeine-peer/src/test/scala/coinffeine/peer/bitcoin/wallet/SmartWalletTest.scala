package coinffeine.peer.bitcoin.wallet

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.peer.bitcoin.wallet.SmartWallet.NotEnoughFunds

class SmartWalletTest extends UnitTest with BitcoinjTest {

  "Smart wallet" must "create a new transaction" in new Fixture {
    val tx = wallet.createTransaction(1.BTC, someAddress)
    Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate).value) shouldBe (-1.BTC)
  }

  it must "create a new transaction with chosen inputs" in new Fixture {
    withFees {
      val inputs = wallet.spendCandidates.take(1)
      val tx = wallet.createTransaction(inputs, 1.BTC, someAddress)
      Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate).value) shouldBe (-1.0001.BTC)
    }
  }

  it must "fail to create a new transaction when insufficient balance" in new Fixture {
    an[NotEnoughFunds] shouldBe thrownBy {
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

  it must "release unpublished deposit funds" in new Fixture {
    val tx = wallet.createMultisignTransaction(signatures, 1.BTC, 0.1.BTC)
    val outputs = wallet.releaseTransaction(tx)
    outputs.toSeq.foldLeft(Bitcoin.Zero)(_ + _.getValue) should be >= 1.BTC
    wallet.estimatedBalance shouldBe initialFunds
  }

  trait Fixture {
    val keyPair = new KeyPair
    val otherKeyPair = new KeyPair
    val signatures = Seq(keyPair, otherKeyPair)
    val initialFunds = 10.BTC
    val someAddress = new KeyPair().toAddress(network)
    val wallet = new SmartWallet(createWallet(keyPair, initialFunds))
  }
}
