package com.coinffeine.common.protocol.messages.brokerage

import com.coinffeine.common.Currency.Euro
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.test.UnitTest
import com.coinffeine.common.{Bid, OrderBookEntry, OrderId}

class PeerOrderRequestsTest extends UnitTest {

  "Peer orders" should "have the same currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerOrderRequests(Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.USD)))
    }
  }
}
