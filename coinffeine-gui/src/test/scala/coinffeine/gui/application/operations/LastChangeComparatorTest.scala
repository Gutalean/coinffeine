package coinffeine.gui.application.operations

import org.bitcoinj.params.TestNet3Params
import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.properties.MutableOrderProperties
import coinffeine.model.currency._
import coinffeine.model.exchange._
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.peer.amounts.DefaultAmountsComponent

class LastChangeComparatorTest extends UnitTest with DefaultAmountsComponent {

  private val baseTimestamp = DateTime.now().minusHours(1)
  val originalOrder =
    Order.randomMarketPrice(Bid, 1.BTC, Euro, timestamp = baseTimestamp)
  val orderWithExchange = originalOrder.withExchange(
    Exchange.create(ExchangeId.random(), BuyerRole, PeerId.random(),
      amountsCalculator.exchangeAmountsFor(0.5.BTC, 200.EUR),
      Exchange.Parameters(100, TestNet3Params.get()),
      createdOn = baseTimestamp.plusMinutes(5)))
  val orderWithCancelledExchange = originalOrder.cancel(baseTimestamp.plusMinutes(10))

  val props1 = new MutableOrderProperties(originalOrder)
  val props2 = new MutableOrderProperties(orderWithExchange)
  val props3 = new MutableOrderProperties(orderWithCancelledExchange)

  val instance = new LastChangeComparator

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
}
