import sbt._

object Dependencies {
  object Versions {
    val akka = "2.3.9"
    val dispatch = "0.11.2"
    val lift = "2.6"
  }

  lazy val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % Versions.akka,
    "com.typesafe.akka" %% "akka-slf4j" % Versions.akka,
    "com.typesafe.akka" %% "akka-persistence-experimental" % Versions.akka,
    "com.github.romix.akka" %% "akka-kryo-serialization" % "0.3.3"
  )
  lazy val akkaTest = Seq(
    "com.typesafe.akka" %% "akka-testkit" % Versions.akka,
    "com.github.dnvriend" %% "akka-persistence-inmemory" % "1.0.0"
  )
  lazy val akkaRemote = "com.typesafe.akka" %% "akka-remote" % Versions.akka
  lazy val bitcoinj = "org.bitcoinj" % "bitcoinj-core" % "0.12.2"
  lazy val commonsIo = "org.apache.commons" % "commons-io" % "1.3.2"
  lazy val dispatch = "net.databinder.dispatch" %% "dispatch-core" % Versions.dispatch
  lazy val gatling = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.1.0",
    "io.gatling" % "test-framework" % "1.0" % "test"
  )
  lazy val h2 = "com.h2database" % "h2" % "1.3.176"
  lazy val htmlunit = "net.sourceforge.htmlunit" % "htmlunit" % "2.15"
  lazy val guava = "com.google.guava" % "guava" % "16.0.1"
  lazy val janino = "org.codehaus.janino" % "janino" % "2.7.8"
  lazy val jaxws = "com.sun.xml.ws" % "jaxws-rt" % "2.2.8"
  lazy val jetty = Seq(
    "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "compile,test",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "compile"
      artifacts Artifact("javax.servlet", "jar", "jar")
  )
  lazy val jline = "jline" % "jline" % "2.12"
  lazy val jodaTime = "joda-time" % "joda-time" % "2.7"
  lazy val jodaConvert = "org.joda" % "joda-convert" % "1.7"
  lazy val liftJson = "net.liftweb" %% "lift-json" % Versions.lift
  lazy val liftWeb = "net.liftweb" %% "lift-webkit" % Versions.lift
  lazy val loggingBackend = Seq(
    "ch.qos.logback" % "logback-classic" % "1.1.2",
    "ch.qos.logback" % "logback-core" % "1.1.2"
  )
  lazy val testLoggingBackend = loggingBackend.map(_ % "test")
  lazy val loggingFacade = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
  )
  lazy val netty = "io.netty" % "netty" % "3.9.2.Final"
  lazy val protobuf = "com.google.protobuf" % "protobuf-java" % "2.6.1"
  lazy val reflections = "org.reflections" % "reflections" % "0.9.9"
  lazy val scalafx = Seq(
    "org.scalafx" %% "scalafx" % "8.0.20-R6",
    "org.controlsfx" % "controlsfx" % "8.0.6"
  )
  lazy val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.0"
  lazy val scalatest = "org.scalatest" %% "scalatest" % "2.1.4"
  lazy val scalaParser = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.1"
  lazy val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.2"
  lazy val scalaz = "org.scalaz" %% "scalaz-core" % "7.1.0"
  lazy val zxing = "com.google.zxing" % "core" % "3.1.0"
}
