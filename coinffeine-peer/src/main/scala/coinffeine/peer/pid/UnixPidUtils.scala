package coinffeine.peer.pid

import java.io.File
import scala.sys.process._

private object UnixPidUtils extends PidUtils {

  private val NullFile = new File("/dev/null")
  private val SuccessExitCode = 0

  override def isRunning(pid: Int): Boolean = (s"ps -p $pid" #> NullFile !) == SuccessExitCode
}
