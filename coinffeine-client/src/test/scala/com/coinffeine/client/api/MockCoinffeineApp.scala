package com.coinffeine.client.api

import com.coinffeine.common.paymentprocessor.PaymentProcessor.Component
import com.coinffeine.common.protocol.ProtocolConstants

class MockCoinffeineApp extends CoinffeineApp {

  override val network = new MockCoinffeineNetwork

  override def wallet: CoinffeineWallet = ???

  override def protocolConstants: ProtocolConstants = ???

  override def marketStats: MarketStats = ???

  override def paymentProcessors: Set[Component] = ???

  override def close(): Unit = ???

  override def observe(handler: EventHandler): Unit = {
    ???
  }
}
