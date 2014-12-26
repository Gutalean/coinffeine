import sbt._
import sbt.Keys._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.MergeStrategy

object Assembly {

  /** Generates SBT settings for creating a standalone jar with the given main class */
  def settings(mainClass: String): Seq[Def.Setting[_]] =
    sbtassembly.Plugin.assemblySettings ++ Seq(
      Keys.mainClass in assembly := Some(mainClass),

      jarName in assembly := moduleName.value + "-standalone.jar",

      excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
        cp filter { file =>
          val name = file.data.getName
          name.contains("saaj-api") || // Exclude duplicated JAX-WS definitions
            name.endsWith("-javadoc.jar") // Exclude javadocs
        }
      },

      mergeStrategy in assembly <<= (mergeStrategy in assembly) { old => {
        case "application.conf" => MergeStrategy.concat
        case levelDbFile if levelDbFile.contains("org/iq80/leveldb") => MergeStrategy.first
        case xmlFile if xmlFile.contains("javax/xml/") => MergeStrategy.first
        case x => old(x)
      }}
    )
}
