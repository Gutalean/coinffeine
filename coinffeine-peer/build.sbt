name := "coinffeine-peer"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.h2 % "test",
  Dependencies.jcommander,
  Dependencies.netty
)
