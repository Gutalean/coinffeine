name := "coinffeine-protocol"

ScoverageKeys.excludedPackages in ScoverageCompile := ".*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.netty,
  Dependencies.protobufRpc,
  Dependencies.reflections % "test"
)
