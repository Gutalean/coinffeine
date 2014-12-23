package coinffeine.peer.pid

import scala.sys.process._

private object WindowsPidUtils extends PidUtils {

  override def isRunning(pid: Int): Boolean =
    (Seq("tasklist", "/FI", s"PID eq $pid")!!) contains pid.toString
}
