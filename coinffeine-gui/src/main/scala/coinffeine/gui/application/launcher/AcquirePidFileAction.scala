package coinffeine.gui.application.launcher

import scala.util.{Failure, Success, Try}

import coinffeine.peer.config.user.LocalAppDataDir
import coinffeine.peer.pid.PidFile

class AcquirePidFileAction {
  private val pidFile = new PidFile(LocalAppDataDir().toFile)

  def apply(): Try[Unit] = pidFile.acquire() match {
    case PidFile.Acquired => Success {
      Runtime.getRuntime.addShutdownHook(new Thread() {
        override def run(): Unit = pidFile.release()
      })
    }
    case PidFile.AlreadyRunning(pid) => Failure(AcquirePidFileAction.AlreadyRunning(pid))
    case PidFile.CannotCreate(cause) => Failure(cause)
  }
}

object AcquirePidFileAction {
  case class AlreadyRunning(pid: Int)
    extends Exception(s"Another Coinffeine instance is running with pid $pid")
}
