package coinffeine.gui.setup

import scala.io.Source
import scalafx.scene.control.{CheckBox, Label}
import scalafx.scene.layout.{StackPane, VBox}
import scalafx.scene.web.WebView

import coinffeine.gui.control.GlyphIcon
import coinffeine.gui.wizard.StepPane

class LicenseAgreementPane extends StackPane with StepPane[SetupConfig] {

  override val icon = GlyphIcon.Number1

  private val licenseText = new WebView() {
    val licenseFile = getClass.getResourceAsStream("/docs/license.html")
    try engine.loadContent(Source.fromInputStream(licenseFile).mkString)
    finally licenseFile.close()
  }

  private val licenseAccepted = new CheckBox("I accept the above license") {
    canContinue <== selected
  }

  content = new VBox() {
    styleClass += "license-pane"
    content = Seq(
      Label("You must accept the Coinffeine End-User License Agreement before continuing"),
      new StackPane {
        styleClass += "license-text"
        content = licenseText
      },
      licenseAccepted)
  }
}
