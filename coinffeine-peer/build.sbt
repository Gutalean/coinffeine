import scoverage.ScoverageSbtPlugin.ScoverageKeys

name := "coinffeine-peer"

ScoverageKeys.coverageExcludedPackages := "scalaxb;soapenvelope11;.*generated.*"

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version)

buildInfoPackage := "coinffeine.peer"

libraryDependencies ++= Dependencies.loggingBackend ++ Dependencies.akka ++ Seq(
  Dependencies.anorm,
  Dependencies.h2,
  Dependencies.htmlunit,
  Dependencies.netty,
  Dependencies.scalacheck % "test",
  Dependencies.scalaParser,
  // Support libraries for scalaxb
  Dependencies.dispatch,
  Dependencies.scalaXml
)
