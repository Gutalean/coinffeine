package coinffeine.common.test

import java.io.File

import org.apache.commons.io.FileUtils

/** Utilities for testing with temporary directories */
object TempDir {
  val DefaultSuffix = "dir"

  def create(suffix: String = DefaultSuffix): File = {
    val file = File.createTempFile("temp", suffix)
    file.delete()
    file.mkdir()
    file
  }

  def withTempDir[T](block: File => T): T = withTempDir(DefaultSuffix)(block)

  def withTempDir[T](suffix: String)(block: File => T): T = {
    val dir = create(suffix)
    try block(dir)
    finally FileUtils.deleteDirectory(dir)
  }
}
