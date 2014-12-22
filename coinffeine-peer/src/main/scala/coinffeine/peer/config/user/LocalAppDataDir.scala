package coinffeine.peer.config.user

import java.io.File
import java.nio.file.Path

import coinffeine.common.Platform

object LocalAppDataDir {

  /** Retrieve the path corresponding to the local app data directory.
    *
    * The local data dir is created if it does not exist.
    */
  def apply(): Path = {
    val path = Platform.detect().userSettingsPath()
    ensureDirExists(path.toFile)
    path
  }

  /** Get the path for a file in the local app data directory.
    *
    * If `ensureCreated` is true, the file is created if it does not exist.
    */
  def getFile(filename: String, ensureCreated: Boolean = true): Path = {
    val path = apply().resolve(filename)
    if (ensureCreated) {
      ensureFileExists(path.toFile)
    }
    path
  }

  private def ensureDirExists(dir: File): Unit = {
    if (!dir.exists()) {
      if (!dir.mkdir()) {
        throw new IllegalStateException(s"cannot create local app data directory in $dir")
      }
    } else if (!dir.isDirectory) {
      throw new IllegalStateException(
        s"there is a file in $dir where local app data directory was expected")
    }
  }

  private def ensureFileExists(file: File): Unit = {
    if (!file.exists()) {
      if (!file.createNewFile()) {
        throw new IllegalStateException(s"cannot create file $file in local app data dir")
      }
    } else if (!file.isFile) {
      throw new IllegalStateException(
        s"there is a directory in $file where local app data file was expected")
    }
  }
}
