package coinffeine.peer.pid

import java.io.{IOException, File}

import scala.util.Try

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils

import coinffeine.common.Platform

class PidFile(file: File, pidUtils: PidUtils) extends LazyLogging {

  def this(baseDir: File) =
    this(new File(baseDir, "coinffeine.pid"), PidUtils.forPlatform(Platform.detect()))

  def acquire(): PidFile.AcquireResult = {
    readCurrentPid()
      .filter(pidUtils.isRunning)
      .fold(createPidFile())(PidFile.AlreadyRunning)
  }

  private def readCurrentPid(): Option[Int] =
    Try(FileUtils.readFileToString(file).toInt).toOption

  private def createPidFile(): PidFile.AcquireResult = try {
    FileUtils.write(file, pidUtils.currentProcessPid().toString)
    PidFile.Acquired
  } catch {
    case cause: IOException => PidFile.CannotCreate(cause)
  }

  def release(): Unit = {
    if (file.isFile) file.delete()
    else logger.warn("Cannot delete pid file at {} because it is not a file", file)
  }
}

object PidFile {
  sealed trait AcquireResult
  case object Acquired extends AcquireResult
  case class AlreadyRunning(pid: Int) extends AcquireResult
  case class CannotCreate(cause: IOException) extends AcquireResult
}
