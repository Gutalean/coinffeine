name := "coinffeine-model"

libraryDependencies ++= Seq(
  Dependencies.bitcoinj,
  Dependencies.h2 % "test",
  Dependencies.jodaConvert
)
