package coinffeine.peer.api.impl

import scala.concurrent.Future

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.{Address, MutableNetworkProperties, WalletActivity}
import coinffeine.model.currency._
import coinffeine.model.properties.{MutableProperty, MutablePropertyMap, Property}
import coinffeine.peer.amounts.DefaultAmountsComponent
import coinffeine.peer.api.CoinffeinePaymentProcessor.Balance
import coinffeine.peer.api._
import coinffeine.peer.api.mock.MockCoinffeineNetwork
import coinffeine.peer.payment.MockPaymentProcessorFactory

class MockCoinffeineApp extends AkkaSpec("testSystem") with CoinffeineApp {

  override val network = new MockCoinffeineNetwork

  override def bitcoinNetwork = new DefaultBitcoinNetwork(new MutableNetworkProperties)

  override def wallet: CoinffeineWallet = new CoinffeineWallet {
    override val balance: Property[Option[BitcoinBalance]] =
      new MutableProperty[Option[BitcoinBalance]](Some(BitcoinBalance.singleOutput(10.BTC)))
    override val primaryAddress: Property[Option[Address]] = null
    override val activity: Property[WalletActivity] =
      new MutableProperty[WalletActivity](WalletActivity(Seq.empty))
    override def transfer(amount: Bitcoin.Amount, address: Address) = ???
  }

  override def marketStats: MarketStats = ???

  override def paymentProcessor: CoinffeinePaymentProcessor = new CoinffeinePaymentProcessor {
    override def accountId = Some("fake-account-id")
    override def currentBalance() = Some(Balance(500.EUR, 10.EUR))
    override val balance = new MutablePropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]]
  }

  override def utils = new CoinffeineUtils {
    override def exchangeAmountsCalculator = new DefaultAmountsComponent {}.amountsCalculator
    override def bitcoinFeeCalculator = new DefaultAmountsComponent {}.bitcoinFeeCalculator
  }

  override def global = ???

  override def start() = Future.successful {}
  override def stop() = Future.successful {}
}

object MockCoinffeineApp {
  val paymentProcessorFactory = new MockPaymentProcessorFactory
}
