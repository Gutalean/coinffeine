package coinffeine.peer.pid

import scala.sys.process._

private object WindowsPidUtils extends PidUtils {
  private val SuccessExitCode = 0

  override def isRunning(pid: Int): Boolean =
    (Seq("tasklist", "/FI", s"PID eq $pid")!) == SuccessExitCode
}
