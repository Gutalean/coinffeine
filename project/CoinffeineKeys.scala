import java.io.File

import sbt._

object CoinffeineKeys {
  val release = taskKey[File]("generate release binaries")
  val techPreview = settingKey[Boolean]("whether this is a technical preview")
}
