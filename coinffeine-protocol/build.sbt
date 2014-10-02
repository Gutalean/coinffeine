name := "coinffeine-protocol"

ScoverageKeys.excludedPackages in ScoverageCompile := ".*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.netty,
  Dependencies.tomp2p exclude (Dependencies.netty.organization, Dependencies.netty.name),
  Dependencies.reflections % "test"
)
