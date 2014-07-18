package coinffeine.peer.api.mock

import java.util.UUID

import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.api._
import coinffeine.peer.api.event.CoinffeineAppEvent
import coinffeine.peer.payment.PaymentProcessor.Component

class MockCoinffeineApp extends CoinffeineApp {

  private val peerId = PeerId(s"test-user:${UUID.randomUUID()}")
  private var handlers: Set[EventHandler] = Set.empty

  override val network = new MockCoinffeineNetwork(peerId)

  override def wallet: CoinffeineWallet = ???

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
