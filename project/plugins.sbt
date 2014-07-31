resolvers in ThisBuild ++= Seq(
  "sonatype-releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
  Resolver.sonatypeRepo("public"),
  Classpaths.sbtPluginReleases
)

addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.3.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.11.2")

// We are using a release candidate as the 0.7.1 has issues with Ubuntu
// https://github.com/playframework/playframework/issues/2968
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.2-RC2")

addSbtPlugin("no.vedaadata" % "sbt-javafx" % "0.6.1")

addSbtPlugin("org.scalaxb" % "sbt-scalaxb" % "1.2.1")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.5.1")

addSbtPlugin("com.ebiznext.sbt.plugins" % "sbt-cxf-wsdl2java" % "0.1.3")
