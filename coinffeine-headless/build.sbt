import AssemblyKeys._
import CoinffeineKeys._

name := "coinffeine-headless"

libraryDependencies ++= Seq(
  Dependencies.janino,
  Dependencies.jline
)

fork := true

jarName in assembly := { s"${name.value}-standalone-${version.value}.jar" }

release := assembly.value
