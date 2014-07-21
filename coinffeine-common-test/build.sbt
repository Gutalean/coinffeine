name := "coinffeine-common-test"

libraryDependencies ++= Dependencies.akka ++ Dependencies.akkaTest ++ Seq(
  Dependencies.protobufRpc,
  Dependencies.scalatest
)
