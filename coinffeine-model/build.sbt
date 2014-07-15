name := "Coinffeine Model"

libraryDependencies ++= Seq(
  Dependencies.bitcoinj,
  Dependencies.h2 % "test",
  Dependencies.jodaConvert
)
