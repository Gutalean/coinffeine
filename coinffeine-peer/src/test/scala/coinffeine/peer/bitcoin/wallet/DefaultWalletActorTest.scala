package coinffeine.peer.bitcoin.wallet

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.concurrent.duration._

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.Both
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{ImmutableTransaction, KeyPair, MutableWalletProperties}
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.peer.bitcoin.wallet.WalletActor.{SubscribeToWalletChanges, UnsubscribeToWalletChanges, WalletChanged}

class DefaultWalletActorTest extends AkkaSpec("WalletActorTest") with BitcoinjTest with Eventually {

  "The wallet actor" must "update wallet primary address upon start" in new Fixture {
    eventually(timeout = Timeout(10.seconds)) {
      properties.primaryAddress.get shouldBe 'nonEmpty
    }
  }

  it must "update the balance upon start" in new Fixture {
    eventually {
      properties.balance.get shouldBe Some(initialBalance)
    }
  }

  it must "create a new transaction" in new Fixture {
    val request = WalletActor.CreateTransaction(1.BTC, someAddress)
    instance ! request
    val depositCreated = expectMsgPF() {
      case WalletActor.TransactionCreated(`request`, responseTx) => responseTx
    }
    val value: Bitcoin.Amount = depositCreated.get.getValue(wallet.delegate)
    value shouldBe (-1.BTC)
  }

  it must "fail to create a new transaction given insufficient balance" in new Fixture {
    val req = WalletActor.CreateTransaction(20.BTC, someAddress)
    instance ! req
    val error = expectMsgType[WalletActor.TransactionCreationFailure]
    error.req shouldBe req
    error.failure.getMessage should include ("Not enough funds")
  }

  it must "create a deposit as a multisig transaction" in new Fixture {
    val funds = givenBlockedFunds(1.1.BTC)
    val request = WalletActor.CreateDeposit(funds, requiredSignatures, 1.BTC, 0.1.BTC)
    instance ! request
    expectMsgPF() {
      case WalletActor.DepositCreated(`request`, depositTx) =>
        wallet.value(depositTx.get) shouldBe (-1.1.BTC)
        Bitcoin.fromSatoshi(depositTx.get.getOutput(0).getValue.value) shouldBe 1.BTC
    }
  }

  it must "fail to create a deposit when there is no enough amount" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val request = WalletActor.CreateDeposit(funds, requiredSignatures, 10000.BTC, 0.BTC)
    instance ! request
    val error = expectMsgType[WalletActor.DepositCreationError]
    error.request shouldBe request
    error.error.getMessage should include ("Not enough funds")
  }

  it must "release unpublished deposit funds" in new Fixture {
    val funds = givenBlockedFunds(1.BTC)
    val request = WalletActor.CreateDeposit(funds, requiredSignatures, 1.BTC, 0.BTC)
    instance ! request
    val reply = expectMsgType[WalletActor.DepositCreated]
    instance ! WalletActor.ReleaseDeposit(reply.tx)
    eventually {
      wallet.estimatedBalance shouldBe initialBalance.amount
    }
  }

  it must "create new key pairs" in new Fixture {
    instance ! WalletActor.CreateKeyPair
    expectMsgClass(classOf[WalletActor.KeyPairCreated])
  }

  it must "update balance property when changed" in new Fixture {
    sendMoneyToWallet(wallet.delegate, 1.BTC)
    val expectedBalance = balancePlusOutputAmount(initialBalance, 1.BTC)
    eventually {
      properties.balance.get shouldBe Some(expectedBalance)
    }
  }

  it must "notify on wallet changes until being unsubscribed" in new Fixture {
    instance ! SubscribeToWalletChanges
    wallet.delegate.importKey(new KeyPair)
    receiveWhile(max = 1.second) {
      case WalletChanged =>
    }
    instance ! UnsubscribeToWalletChanges
    wallet.delegate.importKey(new KeyPair)
    expectNoMsg(1.second)
  }

  val funds1, funds2, funds3 = ExchangeId.random()
  val serializedWallet = new ByteArrayOutputStream()
  var tx: ImmutableTransaction = _

  it must "manage bitcoins blocking with idempotency" in new Fixture {
    instance ! WalletActor.BlockBitcoins(funds3, 3.BTC)
    expectMsg(WalletActor.BlockedBitcoins(funds3))
    instance ! WalletActor.BlockBitcoins(funds3, 3.BTC)
    expectMsg(WalletActor.BlockedBitcoins(funds3))
    instance ! WalletActor.UnblockBitcoins(funds3)
  }

  it must "save the state of the bitcoins blocked" in new Fixture {
    instance ! WalletActor.BlockBitcoins(funds1, 1.BTC)
    expectMsg(WalletActor.BlockedBitcoins(funds1))
    instance ! WalletActor.UnblockBitcoins(funds1)
    instance ! WalletActor.BlockBitcoins(funds2, 2.BTC)
    expectMsg(WalletActor.BlockedBitcoins(funds2))
    instance ! WalletActor.CreateDeposit(funds2, requiredSignatures, 1.BTC, 0.1.BTC)
    tx = expectMsgType[WalletActor.DepositCreated].tx
    system.stop(instance)
    wallet.delegate.saveToFileStream(serializedWallet)
  }

  it must "recover its previous state" in new Fixture {
    override def useLastPersistenceId = true
    override def buildWallet() = SmartWallet.loadFromStream(
      new ByteArrayInputStream(serializedWallet.toByteArray))
    wallet.estimatedBalance shouldBe 8.9.BTC
    instance ! WalletActor.ReleaseDeposit(tx)
    eventually {
      wallet.estimatedBalance shouldBe 10.BTC
    }
    instance ! WalletActor.UnblockBitcoins(funds2)
    instance ! WalletActor.BlockBitcoins(funds3, 1.BTC)
    expectMsg(WalletActor.BlockedBitcoins(funds3))
  }

  var lastPersistenceId = 0

  trait Fixture {
    def useLastPersistenceId: Boolean = false
    val requiredSignatures = Both(new KeyPair, new KeyPair)
    val initialBalance = BitcoinBalance.singleOutput(10.BTC)
    private val persistenceId = {
      if (!useLastPersistenceId) {
        lastPersistenceId += 1
      }
      lastPersistenceId.toString
    }
    protected def buildWallet() = {
      val wallet = createWallet(initialBalance.amount)
      wallet.importKey(requiredSignatures.buyer)
      new SmartWallet(wallet)
    }
    lazy val wallet = buildWallet()
    val properties = new MutableWalletProperties()
    val instance = system.actorOf(DefaultWalletActor.props(properties, wallet, persistenceId))
    val someAddress = new KeyPair().toAddress(network)

    def givenBlockedFunds(amount: Bitcoin.Amount): ExchangeId = {
      val fundsId = ExchangeId.random()
      instance ! WalletActor.BlockBitcoins(fundsId, amount)
      expectMsg(WalletActor.BlockedBitcoins(fundsId))
      fundsId
    }

    def balancePlusOutputAmount(balance: BitcoinBalance, amount: Bitcoin.Amount) = balance.copy(
      estimated = balance.estimated + amount,
      available = balance.estimated + amount,
      minOutput = Some(balance.minOutput.fold(amount) { _ min amount }))
  }
}
