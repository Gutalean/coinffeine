package coinffeine.peer.bitcoin

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.KeyPair
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.SmartWallet.NotEnoughFunds

class SmartWalletTest extends UnitTest with BitcoinjTest {

  "Smart wallet" must "create a new transaction" in new Fixture {
    val tx = wallet.createTransaction(1.BTC, someAddress)
    Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate).value) shouldBe (-1.BTC)
  }

  it must "fail to create a new transaction when insufficient balance" in new Fixture {
    an[NotEnoughFunds] shouldBe thrownBy {
      wallet.createTransaction(20.BTC, someAddress)
    }
  }

  it must "create a multisign transaction" in new Fixture {
    val funds = givenBlockedFunds(1.1.BTC)
    val tx = wallet.createMultisignTransaction(funds, 1.BTC, 0.1.BTC, Seq(keyPair, otherKeyPair))
    wallet.value(tx.get) shouldBe (-1.1.BTC)
    Bitcoin.fromSatoshi(tx.get.getOutput(0).getValue.value) shouldBe 1.BTC
  }

  it must "fail to create a deposit when there is no enough amount" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    a[NotEnoughFunds] shouldBe thrownBy {
      wallet.createMultisignTransaction(funds, 10000.BTC, 0.BTC, Seq(keyPair, otherKeyPair))
    }
  }

  it must "release unpublished deposit funds" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val tx = wallet.createMultisignTransaction(funds, 1.BTC, 0.1.BTC, Seq(keyPair, otherKeyPair))
    wallet.releaseTransaction(tx)
    wallet.estimatedBalance shouldBe initialFunds
  }

  trait Fixture {
    val keyPair = new KeyPair
    val otherKeyPair = new KeyPair
    val initialFunds = 10.BTC
    val someAddress = new KeyPair().toAddress(network)
    val wallet = new SmartWallet(createWallet(keyPair, initialFunds))

    def givenBlockedFunds(amount: Bitcoin.Amount): ExchangeId = {
      val fundsId = ExchangeId.random()
      wallet.blockFunds(fundsId, amount)
      fundsId
    }
  }
}
