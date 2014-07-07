name := "Coinffeine Common Test"

libraryDependencies ++= Dependencies.akka ++ Dependencies.akkaTest ++ Seq(
  Dependencies.protobufRpc,
  Dependencies.scalatest
)
