package coinffeine.peer.bitcoin

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.{KeyPair, BlockedCoinsId}
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency._
import coinffeine.model.currency.Implicits._
import coinffeine.peer.bitcoin.SmartWallet.NotEnoughFunds

class SmartWalletTest extends UnitTest with BitcoinjTest {

  "Smart wallet" must "create a new transaction" in new Fixture {
    val tx = wallet.createTransaction(1.BTC, someAddress)
    Bitcoin.fromSatoshi(tx.get.getValue(wallet.delegate)) should be (-1.BTC)

  }

  it must "fail to create a new transaction when insufficient balance" in new Fixture {
    an[NotEnoughFunds] should be thrownBy {
      wallet.createTransaction(20.BTC, someAddress)
    }
  }

  it must "create a multisign transaction" in new Fixture {
    val funds = givenBlockedFunds(1.1.BTC)
    val tx = wallet.createMultisignTransaction(funds, 1.BTC, 0.1.BTC, Seq(keyPair, otherKeyPair))
    wallet.value(tx.get) should be (-1.1.BTC)
    Bitcoin.fromSatoshi(tx.get.getOutput(0).getValue) should be (1.BTC)
  }

  it must "fail to create a deposit when there is no enough amount" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    a[NotEnoughFunds] should be thrownBy {
      wallet.createMultisignTransaction(funds, 10000.BTC, 0.BTC, Seq(keyPair, otherKeyPair))
    }
  }

  it must "release unpublished deposit funds" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val tx = wallet.createMultisignTransaction(funds, 1.BTC, 0.1.BTC, Seq(keyPair, otherKeyPair))
    wallet.releaseTransaction(tx)
    wallet.balance should be(initialFunds)
  }

  trait Fixture {
    val keyPair = new KeyPair
    val otherKeyPair = new KeyPair
    val initialFunds = 10.BTC
    val someAddress = new KeyPair().toAddress(network)
    val wallet = new SmartWallet(createWallet(keyPair, initialFunds))

    def givenBlockedFunds(amount: BitcoinAmount): BlockedCoinsId = wallet.blockFunds(amount).get
  }
}
