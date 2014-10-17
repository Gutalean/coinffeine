name := "Coinffeine"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.0" cross CrossVersion.full)

libraryDependencies ++= Dependencies.scalafx ++ Seq(
  Dependencies.janino,
  Dependencies.zxing,
  "org.loadui" % "testFx" % "3.1.2"
)

unmanagedJars in Compile += Attributed.blank(file(scala.util.Properties.javaHome) / "/lib/jfxrt.jar")

fork := true

// testOptions in Test += Tests.Argument("-l", "UITest")

jfxSettings

JFX.vendor := "Coinffeine S.L."

JFX.copyright := "Copyright (c) 2013, 2014 Coinffeine S.L."

JFX.license := "Coinffeine License"

JFX.artifactBaseNameValue := "packages"

JFX.mainClass := Some("coinffeine.gui.Main")

JFX.title := "Coinffeine"

JFX.nativeBundles := "all"

JFX.pkgResourcesDir := baseDirectory.value + "/src/deploy"

JFX.verbose := true
