package coinffeine.peer.api.impl

import coinffeine.common.test.AkkaSpec
import coinffeine.model.bitcoin.{Address, KeyPair}
import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Implicits._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.api._
import coinffeine.peer.api.event.CoinffeineAppEvent
import coinffeine.peer.api.mock.MockCoinffeineNetwork
import coinffeine.peer.payment.MockPaymentProcessorFactory

class MockCoinffeineApp extends AkkaSpec("testSystem") with CoinffeineApp {

  private var handlers: Set[EventHandler] = Set.empty

  override val network = new MockCoinffeineNetwork

  override def wallet: CoinffeineWallet = new CoinffeineWallet {
    override def currentBalance() = 56.323523.BTC
    override def transfer(amount: BitcoinAmount, address: Address) = ???
    override def importPrivateKey(address: Address, key: KeyPair) = ???
    override def depositAddress = ???
  }

  override def protocolConstants: ProtocolConstants = ???

  override def marketStats: MarketStats = ???

  override def paymentProcessor: CoinffeinePaymentProcessor = ???

  override def close(): Unit = ???

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
