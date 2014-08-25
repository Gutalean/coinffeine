package coinffeine.model.bitcoin

import scala.util.Random

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._

class PimpMyWalletTest extends UnitTest with BitcoinjTest {

  "Pimp my wallet" must "block funds on create send" in new Fixture {
    val tx = instance.blockFunds(someoneElseAddress, 1.BTC)
    instance.value(tx) should be (-1.BTC)
    instance.balance() should be (initialFunds - instance.valueSentFromMe(tx))
  }

  it must "block funds in create multisign transaction" in new Fixture {
    val tx = instance.blockMultisignFunds(Seq(keyPair, someoneElseKeyPair), 1.BTC, 0.1.BTC)
    instance.value(tx) should be (-1.1.BTC)
    Bitcoin.fromSatoshi(tx.getOutput(0).getValue) should be (1.BTC)
    instance.balance() should be (initialFunds - instance.valueSentFromMe(tx))
  }

  it must "release funds previously blocked on create send" in new Fixture {
    val tx = instance.blockFunds(someoneElseAddress, 1.BTC)
    instance.releaseFunds(tx)
    instance.balance() should be (initialFunds)
  }

  it must "block funds after release" in new Fixture {
    val tx1 = instance.blockFunds(someoneElseAddress, 1.BTC)
    instance.releaseFunds(tx1)
    val tx2 = instance.blockFunds(someoneElseAddress, initialFunds)
    instance.balance() should be (0.BTC)
  }

  it must "reject releasing funds of a transaction not managed by this wallet" in new Fixture {
    val otherWallet = createWallet(new KeyPair, 10.BTC)
    val tx = otherWallet.blockFunds(someoneElseAddress, 5.BTC)
    an [IllegalArgumentException] shouldBe thrownBy {
      instance.releaseFunds(tx)
    }
  }

  it must "consider change funds after tx is broadcast" in new Fixture {
    val tx = instance.blockFunds(someoneElseAddress, 1.BTC)
    sendToBlockChain(tx)
    instance.balance() should be (initialFunds - 1.BTC)
  }

  it must "support a period of arbitrary spent or release operations" in new Fixture {
    var expectedBalance = initialFunds
    for (_ <- 1 to 30) {
      val tx = instance.blockFunds(someoneElseAddress, 0.1.BTC)
      if (Random.nextBoolean()) {
        sendToBlockChain(tx)
        expectedBalance += instance.value(tx)
      } else {
        instance.releaseFunds(tx)
      }
    }
    instance.balance() should be (expectedBalance)
  }

  trait Fixture {
    val keyPair = new KeyPair
    val someoneElseKeyPair = new KeyPair
    val someoneElseAddress = someoneElseKeyPair.toAddress(network)
    val instance = createWallet(keyPair, 10.BTC)
    val initialFunds = instance.balance()
  }
}
