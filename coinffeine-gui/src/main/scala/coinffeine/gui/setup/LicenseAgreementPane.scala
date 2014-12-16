package coinffeine.gui.setup

import scala.io.Source
import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{CheckBox, Label}
import scalafx.scene.layout.{HBox, StackPane, VBox}
import scalafx.scene.web.WebView

import coinffeine.gui.wizard.StepPane

class LicenseAgreementPane extends StackPane with StepPane[SetupConfig] {

  private val licenseText = new WebView() {
    id = "wizard-license-text"
    val licenseFile = getClass.getResourceAsStream("/docs/license.html")
    try {
      val license = Source.fromInputStream(licenseFile).mkString
      engine.loadContent(license)
    } finally {
      licenseFile.close()
    }
  }

  private val licenseAccepted = new CheckBox("I accept the above license") {
    canContinue <== selected
  }

  id = "wizard-license-agreement-pane"
  content = new VBox() {
    content = Seq(
      Label("You must accept the Coinffeine End-User License Agreement before continue."),
      new HBox() {
        styleClass += "webview-wrapper"
        content = licenseText
      },
      licenseAccepted)
  }

  override def bindTo(data: ObjectProperty[SetupConfig]) = {}
}
