name := "coinffeine-peer"

ScoverageKeys.excludedPackages in ScoverageCompile := "scalaxb;soapenvelope11;.*generated.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.h2 % "test",
  Dependencies.jcommander,
  Dependencies.netty,
  // Support libraries for scalaxb
  Dependencies.dispatch,
  Dependencies.scalaParser,
  Dependencies.scalaXml
)
