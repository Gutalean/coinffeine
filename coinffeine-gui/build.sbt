import CoinffeineKeys._

name := "Coinffeine"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)

libraryDependencies ++= Dependencies.scalafx ++ Seq(
  Dependencies.janino,
  Dependencies.zxing,
  "org.loadui" % "testFx" % "3.1.2"
)

unmanagedJars in Compile += Attributed.blank(file(scala.util.Properties.javaHome) / "/lib/jfxrt.jar")

fork := true

// Generate an iss file with the current version interpolated. This should be upgraded to a
// full-fledged sbt plugin if more files or variables are needed

resourceGenerators in Compile <+= (sourceDirectory, crossTarget, version) map {
  (sourceDir, targetDir, version) =>
    def windowsDir(base: File): File = base / "deploy" / "package" / "windows"
    val contents = IO.read(windowsDir(sourceDir) / "Coinffeine.iss.template")
      .replaceAll("\\$\\{version\\}", version)
    val targetFile = windowsDir(targetDir) / "Coinffeine.iss"
    IO.write(targetFile, contents)
    Seq(targetFile)
  }

jfxSettings

JFX.vendor := "Coinffeine S.L."

JFX.copyright := "Copyright (c) 2013-2015 Coinffeine S.L."

JFX.license := "Coinffeine License"

JFX.artifactBaseNameValue := "packages"

JFX.mainClass := Option("coinffeine.gui.Main")

JFX.title := "Coinffeine"

JFX.nativeBundles := "all"

JFX.properties += "file.encoding" -> "UTF-8"

JFX.pkgResourcesDir := Seq(
  baseDirectory.value / "src" / "deploy",
  crossTarget.value / "deploy"
).mkString(":")

JFX.verbose := true

release <<= Def.task {
  JFX.packageJavaFx.value
  crossTarget.value / JFX.output.value.artifactBaseNameValue
}
