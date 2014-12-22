package coinffeine.peer.pid

import java.io.File

import org.apache.commons.io.FileUtils
import org.scalatest.{Inside, Outcome}

import coinffeine.common.test.FixtureUnitTest

class PidFileTest extends FixtureUnitTest with Inside {

  "The PID file" should "not be acquired if a running process already owns it" in { f =>
    val runningPid = 1234
    f.utils.givenRunningProcess(runningPid)
    f.givenExistingPidFile(runningPid)
    f.pidFile.acquire() shouldBe PidFile.AlreadyRunning(1234)
  }

  it should "be acquired if the file contains the PID of a non-running process" in { f =>
    f.givenExistingPidFile(1234)
    f.pidFile.acquire() shouldBe PidFile.Acquired
    f.expectPidFile(f.utils.myPid)
  }

  it should "be acquired if there is no previous PID file" in { f =>
    f.pidFile.acquire() shouldBe PidFile.Acquired
    f.expectPidFile(f.utils.myPid)
  }

  it should "fail if the PID file cannot be overwritten" in { f =>
    f.givenPidFileCannotBeWritten()
    inside (f.pidFile.acquire()) {
      case PidFile.CannotCreate(_) =>
    }
  }

  it should "release a previously acquired PID file" in { f =>
    f.pidFile.acquire() shouldBe PidFile.Acquired
    f.pidFile.release()
    f.expectNoPidFile()
  }

  it should "log but not throw when releasing a not acquired PID file" in { f =>
    f.pidFile.release()
  }

  class PidUtilsStub extends PidUtils {
    val myPid: Int = 42
    var runningProcesses = List(myPid)

    override def currentProcessPid(): Int = myPid
    override def isRunning(pid: Int): Boolean = runningProcesses.contains(pid)

    def givenRunningProcess(pid: Int): Unit = {
      runningProcesses :+= pid
    }
  }

  class FixtureParam {
    private val basePath = {
      val f = File.createTempFile("test", "coinffeine")
      f.delete()
      f.mkdir()
      f
    }
    val file = new File(basePath, "coinffeine.pid")
    val utils = new PidUtilsStub
    val pidFile = new PidFile(file, utils)

    def givenExistingPidFile(pid: Int): Unit = {
      FileUtils.write(file, pid.toString)
    }

    def givenPidFileCannotBeWritten(): Unit = {
      file.mkdir()
    }

    def expectPidFile(pid: Int): Unit = {
      file shouldBe 'file
      FileUtils.readFileToString(file).toInt shouldBe pid
    }

    def expectNoPidFile(): Unit = {
      file.exists() shouldBe false
    }

    def tearDown() : Unit = {
      FileUtils.deleteDirectory(basePath)
    }
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    val fixture = new FixtureParam()
    try {
      withFixture(test.toNoArgTest(fixture))
    } finally {
      fixture.tearDown()
    }
  }
}
