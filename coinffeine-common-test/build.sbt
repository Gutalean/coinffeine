name := "coinffeine-common-test"

libraryDependencies ++= Dependencies.akka ++ Dependencies.akkaTest ++ Seq(
  Dependencies.commonsIo,
  Dependencies.scalatest
)
