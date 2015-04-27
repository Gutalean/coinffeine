import CoinffeineKeys._
import scala.collection.JavaConversions._

name := "coinffeine"

version in ThisBuild := "0.9-SNAPSHOT"

organization in ThisBuild := "com.coinffeine"

scalaVersion in ThisBuild := "2.11.5"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:postfixOps",
  "-unchecked",
  "-Xfuture",
  "-Xlint:-infer-any",
  "-Ywarn-dead-code",
  "-Ywarn-unused-import"
)

javaOptions in ThisBuild ++= {
  def propertiesToCopy(property: Any): Boolean = property match {
    case "config.resource" => true
    case scalaKey: String if scalaKey.startsWith("scala.") => true
    case _ => false
  }
  System.getProperties.entrySet().toSeq
    .filter(e => propertiesToCopy(e.getKey))
    .map(e => s"-D${e.getKey}=${e.getValue}")
}

javacOptions in ThisBuild ++= Seq("-source", "1.8")

compileOrder in ThisBuild := CompileOrder.JavaThenScala

resolvers in ThisBuild ++= Seq(
  "coinffeine-releases" at "http://repository.coinffeine.com/content/repositories/releases/",
  "bitcoinj" at "http://distribution.bitcoinj.googlecode.com/git/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
  "Sonatype-repository" at "https://oss.sonatype.org/content/groups/public",
  Resolver.typesafeRepo("releases"),
  Resolver.bintrayRepo("dnvriend", "maven")
)

libraryDependencies in ThisBuild ++= Dependencies.loggingFacade ++ Dependencies.testLoggingBackend ++ Seq(
  Dependencies.jodaTime,
  Dependencies.scalatest % "test",
  Dependencies.scalaz
)

exportJars in ThisBuild := true

aggregate in release := false

release := {
  val moduleOutputs = Seq(
    (release in Build.headless).value,
    (release in Build.okpaymock).value,
    (release in Build.server).value,
    (release in Build.gui).value
  )
  val releaseDir = target.value / "release" / version.value
  IO.createDirectory(releaseDir)
  for (output <- moduleOutputs) {
    if (output.isDirectory) IO.copyDirectory(output, releaseDir / output.name, overwrite = true)
    else IO.copyFile(output, releaseDir / output.name)
  }
  releaseDir
}

publishTo in ThisBuild := {
  val nexus = "http://nexus.coinffeine.pri"
  if (isSnapshot.value) Some("snapshots" at s"$nexus/content/repositories/snapshots")
  else Some("releases"  at s"$nexus/content/repositories/releases")
}

credentials in ThisBuild += Credentials(Path.userHome / ".ivy2" / ".credentials")

addCommandAlias("compile-all", ";coinffeine/test:compile ;test/test:compile ;benchmark/test:compile")

publishArtifact := false

addCommandAlias("publish-all", ";publish ;server/debian:publish ;okpaymock/debian:publish")

