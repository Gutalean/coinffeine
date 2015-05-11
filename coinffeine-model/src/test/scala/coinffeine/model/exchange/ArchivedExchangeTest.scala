package coinffeine.model.exchange

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.model.ActivityLog
import coinffeine.model.currency.Euro
import coinffeine.model.network.PeerId

class ArchivedExchangeTest extends UnitTest {

  "An archived exchange" should "require a non-empty log of activities" in {
    val metadata = ExchangeMetadata[Euro.type](ExchangeId.random(), BuyerRole, PeerId.random(),
      amounts = null, parameters = null, createdOn = DateTime.now())
    an [IllegalArgumentException] shouldBe thrownBy {
      ArchivedExchange(metadata, ActivityLog.empty, Exchange.noProgress(Euro))
    }
  }
}
