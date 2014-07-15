name := "coinffeine-common"

ScoverageKeys.excludedPackages in ScoverageCompile :=
  "scalaxb;soapenvelope11;.*generated.*;.*protobuf.*"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.bitcoinj,
  Dependencies.h2 % "test",
  Dependencies.jodaConvert,
  Dependencies.netty,
  Dependencies.protobufRpc,
  Dependencies.reflections % "test",
  // Support libraries for scalaxb
  Dependencies.dispatch,
  Dependencies.scalaParser,
  Dependencies.scalaXml
)
