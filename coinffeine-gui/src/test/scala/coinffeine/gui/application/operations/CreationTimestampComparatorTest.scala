package coinffeine.gui.application.operations

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.properties.MutableOrderProperties
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.amounts.DefaultAmountsComponent

class CreationTimestampComparatorTest extends UnitTest with DefaultAmountsComponent {

  private val baseTimestamp = DateTime.now().minusHours(1)
  val props1 = orderPropertiesFor(baseTimestamp)
  val props2 = orderPropertiesFor(baseTimestamp.plusMinutes(1))
  val props3 = orderPropertiesFor(baseTimestamp.plusMinutes(2))

  val instance = new CreationTimestampComparator

  "Last-change comparator" should "compare last event dates" in {
    instance.compare(props1, props2) shouldBe 1
    instance.compare(props2, props3) shouldBe 1
    instance.compare(props1, props3) shouldBe 1

    instance.compare(props2, props1) shouldBe -1
    instance.compare(props3, props2) shouldBe -1
    instance.compare(props3, props1) shouldBe -1

    instance.compare(props1, props1) shouldBe 0
    instance.compare(props2, props2) shouldBe 0
    instance.compare(props3, props3) shouldBe 0
  }

  private def orderPropertiesFor(timestamp: DateTime) =
    new MutableOrderProperties(Order.randomMarketPrice(Bid, 1.BTC, Euro, timestamp))
}
