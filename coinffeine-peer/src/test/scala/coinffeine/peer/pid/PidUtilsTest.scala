package coinffeine.peer.pid

import scala.util.Random

import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Interval
import org.scalatest.time.{Seconds, Span}

import coinffeine.common.Platform
import coinffeine.common.test.UnitTest

class PidUtilsTest extends UnitTest with Eventually {

  val utils = PidUtils.forPlatform(Platform.detect())

  "PID utils" should "get the PID of the current process" in {
    utils.currentProcessPid() should be > 0
    utils.currentProcessPid() shouldBe utils.currentProcessPid()
  }

  it should "tell if a process is running" in {
    utils.isRunning(utils.currentProcessPid()) shouldBe true
  }

  it should "tell is a process is not running" in {
    eventually(Interval(Span(0, Seconds))) {
      utils.isRunning(Random.nextInt(65000))
    }
  }
}
