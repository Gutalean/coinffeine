package coinffeine.model

import org.joda.time.DateTime
import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.ActivityLog.Entry

class ActivityLogTest extends UnitTest with OptionValues {

  sealed trait TestEvent
  case object Start extends TestEvent
  case object Stop extends TestEvent
  case object Reload extends TestEvent

  /** Sample timestamps: one every 10 seconds */
  val timestamps = Stream.iterate(DateTime.parse("2015-01-01T00:00")) { timestamp =>
    timestamp.plusSeconds(10)
  }

  "An activity log" should "have no activities when empty" in {
    ActivityLog.empty.activities should have size 0
  }

  it should "record an activity" in {
    val log = ActivityLog(Start, timestamps.head)
    log.activities shouldBe Seq(Entry(Start, timestamps.head))
  }

  it should "record activities in order" in {
    val log = ActivityLog.empty
      .record(Start, timestamps.head)
      .record(Reload, timestamps(1))
      .record(Stop, timestamps(2))
    log.activities.head.event shouldBe Start
    log.activities.last.event shouldBe Stop
  }

  it should "reject new activities newer than the latest one" in {
    val log = ActivityLog.empty
      .record(Stop, timestamps(10))
      .record(Stop, timestamps(10))
    an [IllegalArgumentException] shouldBe thrownBy {
      log.record(Start, timestamps.head)
    }
  }

  it should "have no most recent activity when empty" in {
    ActivityLog.empty.mostRecent shouldBe 'empty
  }

  it should "find the most recent activity entry when any" in {
    val log = ActivityLog.empty
      .record(Start, timestamps.head)
      .record(Stop, timestamps(1))
      .record(Start, timestamps(2))
      .record(Stop, timestamps(3))
    log.mostRecent.value shouldBe Entry(Stop, timestamps(3))
  }

  it should "find when en event happened for the last time" in {
    val log = ActivityLog.empty
      .record(Start, timestamps.head)
      .record(Stop, timestamps(1))
      .record(Start, timestamps(2))
    log.lastTime(_ == Reload) shouldBe 'empty
    log.lastTime(_ == Stop).value shouldBe timestamps(1)
    log.lastTime(_ == Start).value shouldBe timestamps(2)
  }
}
