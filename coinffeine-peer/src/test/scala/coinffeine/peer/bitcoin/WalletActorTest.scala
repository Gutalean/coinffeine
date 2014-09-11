package coinffeine.peer.bitcoin

import scala.concurrent.duration._

import org.scalatest.concurrent.Eventually

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{MutableWalletProperties, BlockedCoinsId, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Bitcoin
import coinffeine.model.currency.Implicits._
import coinffeine.model.event.{Balance, EventChannelProbe, WalletBalanceChangeEvent}
import coinffeine.peer.CoinffeinePeerActor.{RetrieveWalletBalance, WalletBalance}
import coinffeine.peer.bitcoin.BlockedOutputs.NotEnoughFunds
import coinffeine.peer.bitcoin.WalletActor.{SubscribeToWalletChanges, UnsubscribeToWalletChanges, WalletChanged}

class WalletActorTest extends AkkaSpec("WalletActorTest") with BitcoinjTest with Eventually {

  "The wallet actor" must "update wallet primary address upon start" in new Fixture {
    eventually {
      properties.primaryKeyPair.get should be (Some(keyPair.toAddress(network)))
    }
  }

  it must "update the balance upon start" in new Fixture {
    eventually {
      properties.balance.get should be (Some(initialFunds))
    }
  }

  it must "create a deposit as a multisign transaction" in new Fixture {
    val funds = givenBlockedFunds(1.1.BTC)
    val request = WalletActor.CreateDeposit(funds, Seq(keyPair, otherKeyPair), 1.BTC, 0.1.BTC)
    instance ! request
    expectMsgPF() {
      case WalletActor.DepositCreated(`request`, tx) =>
        wallet.value(tx.get) should be (-1.1.BTC)
        Bitcoin.fromSatoshi(tx.get.getOutput(0).getValue) should be (1.BTC)
    }
  }

  it must "fail to create a deposit when there is no enough amount" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val request = WalletActor.CreateDeposit(funds, Seq(keyPair, otherKeyPair), 10000.BTC, 0.BTC)
    instance ! request
    expectMsgPF() {
      case WalletActor.DepositCreationError(`request`, _: NotEnoughFunds) =>
    }
  }

  it must "release unpublished deposit funds" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val request = WalletActor.CreateDeposit(funds, Seq(keyPair, otherKeyPair), 1.BTC, 0.BTC)
    instance ! request
    val reply = expectMsgType[WalletActor.DepositCreated]
    instance ! WalletActor.ReleaseDeposit(reply.tx)
    eventually {
      wallet.balance() should be(initialFunds)
    }
  }

  it must "create new key pairs" in new Fixture {
    instance ! WalletActor.CreateKeyPair
    expectMsgClass(classOf[WalletActor.KeyPairCreated])
  }

  it must "report wallet balance" in new Fixture {
    instance ! RetrieveWalletBalance
    expectMsg(WalletBalance(10.BTC))
  }

  it must "update balance property when changed" in new Fixture {
    sendMoneyToWallet(wallet, 1.BTC)
    val expectedBalance = initialFunds + 1.BTC
    eventually {
      properties.balance.get should be (Some(expectedBalance))
    }
  }

  it must "notify on wallet changes until being unsubscribed" in new Fixture {
    instance ! SubscribeToWalletChanges
    expectNoMsg(100.millis)
    wallet.addKey(new KeyPair)
    expectMsg(WalletChanged)
    instance ! UnsubscribeToWalletChanges
    expectNoMsg(100.millis)
    wallet.addKey(new KeyPair)
    expectNoMsg(100.millis)
  }

  trait Fixture {
    val keyPair = new KeyPair
    val otherKeyPair = new KeyPair
    val initialFunds = 10.BTC
    val wallet = createWallet(keyPair, initialFunds)
    val eventChannelProbe = EventChannelProbe()
    val properties = new MutableWalletProperties
    val instance = system.actorOf(WalletActor.props(properties, wallet))

    def givenBlockedFunds(amount: BitcoinAmount): BlockedCoinsId = {
      instance ! WalletActor.BlockBitcoins(amount)
      expectMsgClass(classOf[WalletActor.BlockedBitcoins]).id
    }
  }
}
