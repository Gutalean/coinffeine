import scoverage.ScoverageSbtPlugin.ScoverageKeys

name := "coinffeine-overlay"

ScoverageKeys.coverageExcludedPackages := ".*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.scalacheck % "test"
)
