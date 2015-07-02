package coinffeine.common.time

import org.joda.time.{DateTime, Interval}

import coinffeine.common.test.UnitTest

class MonthTest extends UnitTest {

  val monthStart = DateTime.parse("2015-07-01T00:00:00")
  val sometimeDuringTheMonth = DateTime.parse("2015-07-14T12:50:02")
  val monthEnd = DateTime.parse("2015-07-30T23:59:59")
  val nextMonthStart = DateTime.parse("2015-08-01T00:00:00")
  val monthInterval = new Interval(monthStart, nextMonthStart)

  "A month" should "be found from its initial instant" in {
    Month.containing(monthStart) shouldBe monthInterval
  }

  it should "be found from its last instant" in {
    Month.containing(monthEnd) shouldBe monthInterval
  }

  it should "be found from an instant in between" in {
    Month.containing(sometimeDuringTheMonth) shouldBe monthInterval
  }
}
