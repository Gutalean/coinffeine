import AssemblyKeys._
import CoinffeineKeys._

name := "coinffeine-headless"

libraryDependencies ++= Dependencies.loggingBackend ++ Seq(
  Dependencies.janino,
  Dependencies.jline,
  Dependencies.scalaz
)

fork := true

assemblySettings

mainClass in assembly := Some("coinffeine.headless.Main")

jarName in assembly := "coinffeine-headless.jar"

mergeStrategy in assembly <<= (mergeStrategy in assembly) { old => {
  case "application.conf" => MergeStrategy.concat
  case levelDbFile if levelDbFile.contains("org/iq80/leveldb") => MergeStrategy.first
  case x => old(x)
}}

excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
  cp filter { file =>
    val name = file.data.getName
    name.contains("saaj-api") || // Exclude duplicated JAX-WS definitions
      name.endsWith("-javadoc.jar") // Exclude javadocs
  }
}

sources in (Compile, doc) := Seq.empty

release := assembly.value
