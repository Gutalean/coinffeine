package coinffeine.headless.commands

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{Address, Hash, KeyPair}
import coinffeine.model.currency._
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.properties.{MutableProperty, MutablePropertyMap, PropertyMap}
import coinffeine.peer.api._

class StatusCommandTest extends CommandTest {

  "The status command" should "print the available and blocked fiat" in new Fixture {
    commandOutput() should include("FIAT: --")
    app.fiatBalance.set(Some(CoinffeinePaymentProcessor.Balance(0.05.EUR)))
    commandOutput() should include("FIAT: 0.05 EUR")
    app.fiatBalance.set(Some(CoinffeinePaymentProcessor.Balance(123.45.EUR, 20.3.EUR)))
    commandOutput() should include("FIAT: 123.45 EUR (20.30 EUR blocked)")
  }

  it should "print the bitcoin wallet balances" in new Fixture {
    commandOutput() should include("BTC: --")
    app.btcBalance.set(Some(BitcoinBalance(
      estimated = 10.BTC, available = 8.BTC, minOutput = Some(0.01.BTC))))
    commandOutput() should include("BTC: 10.00000000 BTC estimated, 8.00000000 BTC available")
    app.btcBalance.set(Some(BitcoinBalance(
      estimated = 10.BTC, available = 8.BTC, blocked = 2.BTC, minOutput = Some(0.01.BTC))))
    commandOutput() should include(
      "BTC: 10.00000000 BTC estimated, 8.00000000 BTC available (2.00000000 BTC blocked)")
  }

  it should "print the current bitcoin wallet key" in new Fixture {
    commandOutput() should include("Wallet address: --")
    val address = new KeyPair().toAddress(CoinffeineUnitTestNetwork)
    app.primaryAddress.set(Some(address))
    commandOutput() should include("Wallet address: " + address)
  }

  trait Fixture {
    val app = new MockCoinffeineApp
    val command = new StatusCommand(app)
    def commandOutput(): String = executeCommand(command)
  }

  class MockCoinffeineApp extends CoinffeineApp {
    val fiatBalance = new MutableProperty[Option[CoinffeinePaymentProcessor.Balance]](None)
    val btcBalance = new MutableProperty[Option[BitcoinBalance]](None)
    val primaryAddress = new MutableProperty[Option[Address]](None)

    override def network: CoinffeineNetwork = ???
    override def stop(timeout: FiniteDuration): Future[Unit] = ???
    override def bitcoinNetwork: BitcoinNetwork = ???
    override def utils: CoinffeineUtils = ???
    override def marketStats: MarketStats = ???
    override def wallet = new CoinffeineWallet {
      override val balance = btcBalance
      override def transfer(amount: Bitcoin.Amount, address: Address): Future[Hash] = ???
      override val activity = null
      override val primaryAddress = MockCoinffeineApp.this.primaryAddress
    }
    override def paymentProcessor = new CoinffeinePaymentProcessor {
      override def currentBalance(): Option[CoinffeinePaymentProcessor.Balance] = fiatBalance.get
      override def accountId: Option[AccountId] = ???
      override val balance: PropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]] =
        new MutablePropertyMap
    }
    override def start(timeout: FiniteDuration): Future[Unit] = ???
  }
}
