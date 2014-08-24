package coinffeine.peer.api.impl

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.bitcoin.{Address, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._
import coinffeine.peer.api.CoinffeinePaymentProcessor.Balance
import coinffeine.peer.api._
import coinffeine.model.event.CoinffeineAppEvent
import coinffeine.peer.api.mock.MockCoinffeineNetwork
import coinffeine.peer.payment.MockPaymentProcessorFactory

class MockCoinffeineApp extends AkkaSpec("testSystem") with CoinffeineApp {

  private var handlers: Set[EventHandler] = Set.empty

  override val network = new MockCoinffeineNetwork

  override def wallet: CoinffeineWallet = new CoinffeineWallet {
    override def currentBalance() = Some(56.323523.BTC)
    override def transfer(amount: BitcoinAmount, address: Address) = ???
    override def importPrivateKey(address: Address, key: KeyPair) = ???
    override def depositAddress = ???
  }

  override def marketStats: MarketStats = ???

  override def paymentProcessor: CoinffeinePaymentProcessor = new CoinffeinePaymentProcessor {
    override def accountId = "fake-account-id"
    override def currentBalance() = Some(Balance(500.EUR, 10.EUR))
  }

  override def start(timeout: FiniteDuration) = Future.successful {}
  override def stop(timeout: FiniteDuration) = Future.successful {}

  override def observe(handler: EventHandler): Unit = {
    handlers += handler
  }

  def produceEvent(event: CoinffeineAppEvent): Unit = {
    for (h <- handlers if h.isDefinedAt(event)) { h(event) }
  }
}

object MockCoinffeineApp {
  val paymentProcessorFactory = new MockPaymentProcessorFactory
}
