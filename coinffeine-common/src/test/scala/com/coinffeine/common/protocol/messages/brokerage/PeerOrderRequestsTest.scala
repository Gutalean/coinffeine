package com.coinffeine.common.protocol.messages.brokerage

import coinffeine.model.currency.Currency
import Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.order.{OrderId, Bid, OrderBookEntry}
import com.coinffeine.common.test.UnitTest

class PeerOrderRequestsTest extends UnitTest {

  "Peer orders" should "have the same currency" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      PeerOrderRequests(Market(Euro), Seq(OrderBookEntry(OrderId.random(), Bid, 1.BTC, 400.USD)))
    }
  }
}
