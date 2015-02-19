package coinffeine.gui.application.launcher

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

import coinffeine.peer.pid.PidFile

class AcquirePidFileAction(dataDir: File) {
  private val pidFile = new PidFile(dataDir)

  def apply(): Future[Unit] = Future {
    pidFile.acquire() match {
      case PidFile.Acquired => Runtime.getRuntime.addShutdownHook(new Thread() {
        override def run(): Unit = pidFile.release()
      })
      case PidFile.AlreadyRunning(pid) => throw new AcquirePidFileAction.AlreadyRunning(pid)
      case PidFile.CannotCreate(cause) => throw cause
    }
  }(ExecutionContext.global)
}

object AcquirePidFileAction {
  case class AlreadyRunning(pid: Int)
    extends Exception(s"Another Coinffeine instance is running with pid $pid")
}
