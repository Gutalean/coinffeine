name := "Coinffeine Model"

libraryDependencies ++= Dependencies.akka ++ Seq(
  Dependencies.bitcoinj,
  Dependencies.h2 % "test",
  Dependencies.jodaConvert
)
