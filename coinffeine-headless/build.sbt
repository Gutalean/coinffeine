import AssemblyKeys._
import CoinffeineKeys._

name := "coinffeine-headless"

libraryDependencies ++= Seq(
  Dependencies.janino,
  Dependencies.jline,
  Dependencies.scalaz
)

fork := true

jarName in assembly := { s"${name.value}-standalone-${version.value}" }

release := assembly.value
