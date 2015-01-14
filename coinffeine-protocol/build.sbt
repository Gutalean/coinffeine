import scoverage.ScoverageSbtPlugin.ScoverageKeys

name := "coinffeine-protocol"

ScoverageKeys.coverageExcludedPackages := ".*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.reflections % "test"
)
