package coinffeine.model.exchange

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.network.PeerId
import coinffeine.model.{ActivityLog, Both}

class ArchivedExchangeTest extends UnitTest {

  "An archived exchange" should "require a non-empty log of activities" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      ArchivedExchange(
        id = ExchangeId.random(),
        role = BuyerRole,
        exchangedBitcoin = Both(1.BTC, 1.0003.BTC),
        exchangedFiat = Both(101.EUR, 100.EUR),
        counterpartId = PeerId.random(),
        lockTime = 12345000,
        log = ActivityLog.empty,
        progress = Exchange.noProgress
      )
    }
  }
}
