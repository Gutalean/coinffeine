package coinffeine.headless.commands

import scala.concurrent.Future

import coinffeine.common.properties.MutableProperty
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{Address, Hash, KeyPair}
import coinffeine.model.currency._
import coinffeine.model.currency.balance.{BitcoinBalance, FiatBalance, FiatBalances}
import coinffeine.model.payment.PaymentProcessor.AccountId
import coinffeine.model.util.Cached
import coinffeine.peer.api._
import coinffeine.peer.payment.okpay.OkPayApiCredentials

class StatusCommandTest extends CommandTest {

  "The status command" should "print the available and blocked fiat" in new Fixture {
    commandOutput() should include("FIAT: --")
    app.fiatBalances.set(Cached.fresh(FiatBalances(
      FiatAmounts.fromAmounts(0.05.EUR),
      FiatAmounts.fromAmounts(0.EUR),
      FiatAmounts.fromAmounts()
    )))
    commandOutput() should include("FIAT: 0.05EUR")
    app.fiatBalances.set(Cached.fresh(FiatBalances(
      FiatAmounts.fromAmounts(123.45.EUR),
      FiatAmounts.fromAmounts(20.3.EUR),
      FiatAmounts.fromAmounts()
    )))
    commandOutput() should include("FIAT: 123.45EUR (20.30EUR blocked)")
    app.fiatBalances.set(Cached.fresh(FiatBalances(
      FiatAmounts.fromAmounts(123.45.EUR, 40.USD),
      FiatAmounts.fromAmounts(20.3.EUR),
      FiatAmounts.fromAmounts()
    )))
    commandOutput() should include("FIAT: 123.45EUR (20.30EUR blocked) 40.00USD")
  }

  it should "print the bitcoin wallet balances" in new Fixture {
    commandOutput() should include("BTC: --")
    app.btcBalance.set(Some(BitcoinBalance(
      estimated = 10.BTC, available = 8.BTC, minOutput = Some(0.01.BTC))))
    commandOutput() should include(
      "BTC: 10.00000000BTC estimated, 8.00000000BTC available (min output of 0.01000000BTC)")
    app.btcBalance.set(Some(BitcoinBalance(
      estimated = 10.BTC, available = 8.BTC, blocked = 2.BTC, minOutput = Some(0.01.BTC))))
    commandOutput() should include(
      "BTC: 10.00000000BTC estimated, 8.00000000BTC available " +
        "(2.00000000BTC blocked, min output of 0.01000000BTC)")
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
    val fiatBalances = new MutableProperty[Cached[FiatBalances]](Cached.stale(FiatBalances.empty))
    val btcBalance = new MutableProperty[Option[BitcoinBalance]](None)
    val primaryAddress = new MutableProperty[Option[Address]](None)

    override def network: CoinffeineNetwork = ???
    override def operations: CoinffeineOperations = ???
    override def stop(): Future[Unit] = ???
    override def bitcoinNetwork: BitcoinNetwork = ???
    override def utils: CoinffeineUtils = ???
    override def marketStats: MarketStats = ???
    override def wallet = new CoinffeineWallet {
      override val balance = btcBalance
      override def transfer(amount: BitcoinAmount, address: Address): Future[Hash] = ???
      override val activity = null
      override val primaryAddress = MockCoinffeineApp.this.primaryAddress
    }
    override def paymentProcessor = new CoinffeinePaymentProcessor {
      override def currentBalance(): Option[FiatBalance] = None
      override def accountId: Option[AccountId] = ???
      override val balances = fiatBalances
      override def refreshBalances() = {}
      override def testCredentials(credentials: OkPayApiCredentials) = ???
    }
    override def alarms = ???
    override def start(): Future[Unit] = ???
  }
}
