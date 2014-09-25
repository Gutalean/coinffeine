package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.{WalletActivity, Address, KeyPair}
import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, BitcoinBalance}
import coinffeine.model.properties.Property
import coinffeine.peer.api.CoinffeinePaymentProcessor.Balance
import coinffeine.peer.api._
import coinffeine.peer.api.mock.MockCoinffeineNetwork
import coinffeine.peer.payment.MockPaymentProcessorFactory

class MockCoinffeineApp extends AkkaSpec("testSystem") with CoinffeineApp {

  override val network = new MockCoinffeineNetwork

  override def bitcoinNetwork = ???

  override def wallet: CoinffeineWallet = new CoinffeineWallet {
    override val balance: Property[Option[BitcoinBalance]] = null
    override val primaryAddress: Property[Option[Address]] = null
    override val activity: Property[WalletActivity] = null
    override def transfer(amount: BitcoinAmount, address: Address) = ???
    override def importPrivateKey(address: Address, key: KeyPair) = ???
  }

  override def marketStats: MarketStats = ???

  override def paymentProcessor: CoinffeinePaymentProcessor = new CoinffeinePaymentProcessor {
    override def accountId = "fake-account-id"
    override def currentBalance() = Some(Balance(500.EUR, 10.EUR))
    override val balance = null
  }

  override def utils = ???

  override def start(timeout: FiniteDuration) = Future.successful {}
  override def stop(timeout: FiniteDuration) = Future.successful {}
}

object MockCoinffeineApp {
  val paymentProcessorFactory = new MockPaymentProcessorFactory
}
