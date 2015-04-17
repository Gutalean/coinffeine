package coinffeine.gui.application.operations

import coinffeine.common.test.UnitTest
import org.joda.time.{Period, DateTime}

class DateTimePrinterTest extends UnitTest {

  "Date time printer" should "print a seconds-order period" in new Fixture {
    printElapsed(elapsed(now)) shouldBe "just now"
    printElapsed(elapsed(now.minusSeconds(2))) shouldBe "just now"
    printElapsed(elapsed(now.minusSeconds(59))) shouldBe "just now"
  }

  it should "print a minutes-order period" in new Fixture {
    printElapsed(elapsed(now.minusMinutes(14))) shouldBe "14m ago"
    printElapsed(elapsed(now.minusMinutes(14).minusSeconds(2))) shouldBe "14m ago"
  }

  it should "print a hours-order period" in new Fixture {
    printElapsed(elapsed(now.minusHours(3))) shouldBe "3h ago"
    printElapsed(elapsed(now.minusHours(3).minusMinutes(14))) shouldBe "3h 14m ago"
    printElapsed(elapsed(now.minusHours(3).minusMinutes(14).minusSeconds(39))) shouldBe "3h 14m ago"
  }

  it should "print a days-order period" in new Fixture {
    printElapsed(elapsed(now.minusDays(5).minusHours(3).minusMinutes(14))) shouldBe "5d 3h ago"
  }

  it should "print a months-order period" in new Fixture {
    val t1 = DateTime.parse("2012-01-25T12:00:00")
    val t2 = DateTime.parse("2012-03-25T12:00:00")
    printElapsed(elapsed(t1, t2)) shouldBe "25Jan2012"
  }
  
  it should "print dates" in new Fixture {
    printDate(DateTime.parse("2012-01-25T12:31:12")) shouldBe "January 25, 2012"
  }

  trait Fixture {
    val now = DateTime.now()
    val printer = new DateTimePrinter
    val printElapsed = (printer.printElapsed _).tupled
    val printDate = printer.printDate _

    def elapsed(instant: DateTime, to: DateTime = now): (DateTime, Period) =
      (instant, new Period(instant, now))
  }
}
