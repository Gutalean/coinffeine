import scoverage.ScoverageSbtPlugin.ScoverageKeys

name := "coinffeine-peer"

ScoverageKeys.coverageExcludedPackages := "scalaxb;soapenvelope11;.*generated.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.h2 % "test",
  Dependencies.netty,
  Dependencies.scalacheck % "test",
  Dependencies.scalaz,
  // Support libraries for scalaxb
  Dependencies.dispatch,
  Dependencies.scalaParser,
  Dependencies.scalaXml,
  Dependencies.htmlunit
)
