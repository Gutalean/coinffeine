import com.ebiznext.sbt.plugins.CxfWsdl2JavaPlugin
import com.ebiznext.sbt.plugins.CxfWsdl2JavaPlugin.cxf._
import sbt.Keys._
import sbt._
import sbtprotobuf.{ProtobufPlugin => PB}
import sbtscalaxb.Plugin.ScalaxbKeys._
import sbtscalaxb.Plugin.scalaxbSettings
import scoverage.ScoverageSbtPlugin

object Build extends sbt.Build {

  object Versions {
    val akka = "2.3.3"
    val dispatch = "0.11.1"
  }

  object Dependencies {
    lazy val akka = Seq(
      "com.typesafe.akka" %% "akka-actor" % Versions.akka,
      "com.typesafe.akka" %% "akka-slf4j" % Versions.akka
    )
    lazy val akkaTest = Seq(
      "com.typesafe.akka" %% "akka-testkit" % Versions.akka
    )
    lazy val bitcoinj = "com.google" % "bitcoinj" % "0.11.3"
    lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % Versions.dispatch
    lazy val h2 = "com.h2database" % "h2" % "1.3.175"
    lazy val jcommander = "com.beust" % "jcommander" % "1.35"
    lazy val jodaTime = "joda-time" % "joda-time" % "2.3"
    lazy val jodaConvert = "org.joda" % "joda-convert" % "1.6"
    lazy val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.1.2"
    lazy val logbackCore = "ch.qos.logback" % "logback-core" % "1.1.2"
    lazy val mockito = "org.mockito" % "mockito-all" % "1.9.5"
    lazy val netty = "io.netty" % "netty-all" % "4.0.19.Final"
    lazy val protobuf = "com.google.protobuf" % "protobuf-java" % "2.5.0"
    lazy val protobufRpc = "com.googlecode.protobuf-rpc-pro" % "protobuf-rpc-pro-duplex" % "3.0.8"
    lazy val reflections = "org.reflections" % "reflections" % "0.9.9-RC1"
    lazy val scalafx = Seq(
      "org.scalafx" %% "scalafx" % "8.0.0-R4",
      "org.controlsfx" % "controlsfx" % "8.0.6"
    )
    lazy val scalatest = "org.scalatest" %% "scalatest" % "2.1.7"
    lazy val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
    lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
    lazy val slf4j = "org.slf4j" % "slf4j-api" % "1.7.7"
    lazy val zxing = "com.google.zxing" % "core" % "3.1.0"
  }

  lazy val root = (Project(id = "coinffeine", base = file("."))
    aggregate(peer, protocol, model, commonTest, gui, server, test)
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
  )

  lazy val server = (Project(id = "server", base = file("coinffeine-server"))
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
    dependsOn(peer % "compile->compile;test->test", commonTest % "test->compile")
  )

  lazy val peer = (Project(id = "peer", base = file("coinffeine-peer"))
    settings(scalaxbSettings: _*)
    settings(
      sourceGenerators in Compile <+= scalaxb in Compile,
      packageName in (Compile, scalaxb) := "coinffeine.peer.payment.okpay.generated",
      dispatchVersion in (Compile, scalaxb) := Versions.dispatch,
      async in (Compile, scalaxb) := true
    )
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
    dependsOn(
      model % "compile->compile;test->test",
      protocol % "compile->compile;test->test",
      commonTest % "test->compile"
    )
  )

  lazy val protocol = (Project(id = "protocol", base = file("coinffeine-protocol"))
    settings(PB.protobufSettings: _*)
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
    dependsOn(model % "compile->compile;test->test", commonTest % "test->compile")
  )

  lazy val model = (Project(id = "model", base = file("coinffeine-model"))
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
    dependsOn(commonTest % "test->compile")
  )

  lazy val commonTest = (Project(id = "common-test", base = file("coinffeine-common-test"))
    settings(PB.protobufSettings: _*)
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
  )

  lazy val gui = (Project(id = "gui", base = file("coinffeine-gui"))
    settings(ScoverageSbtPlugin.instrumentSettings: _*)
    dependsOn(peer % "compile->compile;test->test", commonTest)
  )

  lazy val test = (Project(id = "test", base = file("coinffeine-test"))
    dependsOn(peer, server, commonTest % "compile->compile;test->compile")
  )
}
