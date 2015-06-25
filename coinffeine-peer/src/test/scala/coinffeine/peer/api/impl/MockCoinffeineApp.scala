package coinffeine.peer.api.impl

import scala.concurrent.Future

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.properties.{MutableProperty, MutablePropertyMap, Property}
import coinffeine.model.bitcoin.{Address, TransactionSizeFeeCalculator, WalletActivity}
import coinffeine.model.currency._
import coinffeine.model.currency.{FiatBalance, BitcoinBalance}
import coinffeine.peer.amounts.DefaultAmountsCalculator
import coinffeine.peer.api.CoinffeinePaymentProcessor.Balance
import coinffeine.peer.api._
import coinffeine.peer.api.mock.MockCoinffeineOperations
import coinffeine.peer.payment.MockPaymentProcessorFactory
import coinffeine.peer.properties.bitcoin.DefaultNetworkProperties
import coinffeine.protocol.properties.DefaultCoinffeineNetworkProperties

class MockCoinffeineApp extends AkkaSpec("testSystem") with CoinffeineApp {

  override val network = new DefaultCoinffeineNetwork(new DefaultCoinffeineNetworkProperties)

  override def bitcoinNetwork = new DefaultBitcoinNetwork(new DefaultNetworkProperties)

  override def operations = new MockCoinffeineOperations

  override def wallet: CoinffeineWallet = new CoinffeineWallet {
    override val balance: Property[Option[BitcoinBalance]] =
      new MutableProperty[Option[BitcoinBalance]](Some(BitcoinBalance.singleOutput(10.BTC)))
    override val primaryAddress: Property[Option[Address]] = null
    override val activity: Property[WalletActivity] =
      new MutableProperty[WalletActivity](WalletActivity(Seq.empty))
    override def transfer(amount: BitcoinAmount, address: Address) = ???
  }

  override def marketStats: MarketStats = ???

  override def paymentProcessor: CoinffeinePaymentProcessor = new CoinffeinePaymentProcessor {
    override def accountId = Some("fake-account-id")
    override def currentBalance() = Some(Balance(500.EUR, 10.EUR))
    override val balance = new MutablePropertyMap[FiatCurrency, FiatBalance]
  }

  override def utils = new CoinffeineUtils {
    override def exchangeAmountsCalculator = new DefaultAmountsCalculator()
    override def bitcoinFeeCalculator = TransactionSizeFeeCalculator
  }

  override def alarms = ???

  override def start() = Future.successful {}
  override def stop() = Future.successful {}
}

object MockCoinffeineApp {
  val paymentProcessorFactory = new MockPaymentProcessorFactory
}
