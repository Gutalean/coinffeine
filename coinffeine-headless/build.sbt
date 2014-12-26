import AssemblyKeys._
import CoinffeineKeys._

name := "coinffeine-headless"

libraryDependencies ++= Dependencies.loggingBackend ++ Seq(
  Dependencies.janino,
  Dependencies.jline,
  Dependencies.scalaz
)

fork := true

release := assembly.value
