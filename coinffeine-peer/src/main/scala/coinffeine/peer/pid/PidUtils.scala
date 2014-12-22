package coinffeine.peer.pid

import java.lang.management.ManagementFactory

import coinffeine.common.Platform

trait PidUtils {

  /** PID of the current process */
  def currentProcessPid(): Int =
    ManagementFactory.getRuntimeMXBean.getName.takeWhile(_.isDigit).toInt

  /** Detect if a process is running by PID */
  def isRunning(pid: Int): Boolean
}

object PidUtils {
  def forPlatform(platform: Platform): PidUtils = platform match {
    case Platform.Linux | Platform.Mac => UnixPidUtils
    case Platform.Windows => WindowsPidUtils
  }
}
