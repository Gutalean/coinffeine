package coinffeine.gui.wizard

import scalafx.Includes._
import scalafx.beans.property.{IntegerProperty, ObjectProperty}
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.{AnchorPane, BorderPane, HBox}
import scalafx.scene.text.Font
import scalafx.stage.{Stage, StageStyle}

import coinffeine.gui.scene.{Stylesheets, CoinffeineScene}

/** Step-by-step wizard that accumulates information of type Data.
  *
  * @param steps        Sequence of wizard steps
  * @param initialData  Initial value for the wizard
  * @param wizardTitle  Wizard title
  * @tparam Data        Type of the wizard result
  */
class Wizard[Data](steps: Seq[StepPane[Data]], initialData: Data, wizardTitle: String)
    extends Stage(StageStyle.UTILITY) {
  private val data = new ObjectProperty[Data](this, "wizardData", initialData)
  private val stepNumber = steps.size
  private val currentStep = new IntegerProperty(this, "currentStep", 0)
  private val currentStepPane = new ObjectProperty(this, "currentStepPane", steps.head)

  def run(): Data = {
    initializeSteps()
    showAndWait()
    data.value
  }

  private val wizardHeader = {
    val progress = new ProgressIndicator(stepNumber, currentStep).pane
    val title = new Label("Initial setup")
    new AnchorPane {
      id = "wizard-header-pane"
      content = Seq(title, progress)
      AnchorPane.setTopAnchor(title, 15)
      AnchorPane.setLeftAnchor(title, 22)
      AnchorPane.setTopAnchor(progress, 20)
      AnchorPane.setRightAnchor(progress, 15)
    }
  }

  private val backButton = new Button("< Back") {
    visible <== currentStep =!= 1
    handleEvent(ActionEvent.ACTION) { () => changeToStep(currentStep.value - 1) }
  }

  private val nextButton = new Button {
    text <== when(currentStep === stepNumber) choose "Finish" otherwise "Next >"
    disable = true
    handleEvent(ActionEvent.ACTION) { () =>
      if (currentStep.value < stepNumber) changeToStep(currentStep.value + 1) else close()
    }
  }

  private val wizardFooter = {
    val buttonBox = new HBox(spacing = 5) {
      content = Seq(backButton, nextButton)
    }
    new AnchorPane {
      id = "wizard-footer-pane"
      content = buttonBox
      AnchorPane.setTopAnchor(buttonBox, 5)
      AnchorPane.setRightAnchor(buttonBox, 15)
    }
  }

  private val rootWizardPane: BorderPane = new BorderPane {
    id = "wizard-root-pane"
    top = wizardHeader
    center = steps.head
    bottom = wizardFooter
  }

  title = Wizard.this.wizardTitle
  resizable = false
  scene = new CoinffeineScene(Stylesheets.Wizard) {
    root = rootWizardPane
  }

  private def initializeSteps(): Unit = {
    currentStep.onChange {
      val stepPane = steps(currentStep.value - 1)
      stepPane.bindTo(data)
      currentStepPane.value = stepPane
      rootWizardPane.center = stepPane
      nextButton.disable <== stepPane.canContinue.not()
    }
    currentStep.value = 1
  }

  private def changeToStep(index: Int): Unit = {
    currentStep.value = index
  }
}

