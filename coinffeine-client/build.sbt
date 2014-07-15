name := "coinffeine-client"

libraryDependencies ++= Seq(
  Dependencies.h2 % "test",
  Dependencies.jcommander,
  Dependencies.netty
)
