package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.{Bid, Order, OrderId}
import com.coinffeine.common.Currency.Euro
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.test.UnitTest

class PeerPositionsTest extends UnitTest {

  "Peer positions" should "have the same currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerPositions(Market(Euro), Set(Order(OrderId.random(), Bid, 1.BTC, 400.USD)))
    }
  }
}
