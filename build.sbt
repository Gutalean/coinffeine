import scala.collection.JavaConversions._

name := "coinffeine"

version in ThisBuild := "0.3-SNAPSHOT"

organization in ThisBuild := "com.coinffeine"

scalaVersion in ThisBuild := "2.11.4"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation", "-feature", "-language:postfixOps", "-language:existentials")

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

// The following props are needed to avoid overriding max UDP sockets,
// which by default is too low for TomP2P. We have to run tests in fork mode with
// Java options merged from parent process and a custom one

javaOptions in ThisBuild += "-Dsun.net.maxDatagramSockets=128"

fork in ThisBuild := true

compileOrder in ThisBuild := CompileOrder.JavaThenScala

resolvers in ThisBuild ++= Seq(
  "coinffeine-releases" at "http://repository.coinffeine.com/nexus/content/repositories/releases/",
  "bitcoinj" at "http://distribution.bitcoinj.googlecode.com/git/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
  "Sonatype-repository" at "https://oss.sonatype.org/content/groups/public",
  "typesafe" at "http://repo.typesafe.com/typesafe/releases/",
  "tomp2p" at "http://tomp2p.net/dev/mvn/"
)

addCommandAlias("test", "test-only * -- -l UITest")

addCommandAlias("test-gui", "test-only * -- -n UITest")

libraryDependencies in ThisBuild ++= Dependencies.loggingFacade ++ Dependencies.testLoggingBackend ++ Seq(
  Dependencies.jodaTime,
  Dependencies.scalatest % "test"
)

exportJars in ThisBuild := true
