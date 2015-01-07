name := "coinffeine-overlay"

ScoverageKeys.excludedPackages in ScoverageCompile := ".*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.scalacheck % "test"
)
