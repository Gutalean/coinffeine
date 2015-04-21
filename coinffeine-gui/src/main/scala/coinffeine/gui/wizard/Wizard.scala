package coinffeine.gui.wizard

import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, IntegerProperty, ObjectProperty}
import scalafx.event.{ActionEvent, Event}
import scalafx.scene.control.Button
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.scene.shape.Rectangle
import scalafx.stage.{Window, Modality, Stage, StageStyle}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphLabel}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.NodeStyles.HExpand
import coinffeine.gui.scene.styles.Stylesheets

/** Step-by-step wizard that accumulates information of type Data.
  *
  * @param steps        Sequence of wizard steps
  * @param initialData  Initial value for the wizard
  * @param wizardTitle  Wizard title
  * @tparam Data        Type of the wizard result
  */
class Wizard[Data](steps: Seq[StepPane[Data]],
                   initialData: Data,
                   wizardTitle: String,
                   additionalStyles: Seq[String] = Seq.empty) extends Stage(StageStyle.UTILITY) {

  private val data = initialData
  private val cancelled = new BooleanProperty(this, "cancelled", false)
  private val stepNumber = steps.size
  private val currentStep = new IntegerProperty(this, "currentStep", 0)
  private val currentStepPane = new ObjectProperty(this, "currentStepPane", steps.head)

  def run(parentWindow: Option[Window] = None): Data = {
    initializeSteps()
    initModality(Modality.WINDOW_MODAL)
    parentWindow.foreach(initOwner)
    showAndWait()
    if (cancelled.value) { throw new Wizard.CancelledByUser("The wizard was cancelled by the user") }
    data
  }

  def cancel(): Unit = { cancelled.set(true) }

  private val wizardHeader = new HBox {
    styleClass += "header"
    content = steps.zipWithIndex.flatMap { case (step, index) =>
      val icon = new GlyphLabel with HExpand {
        icon <== currentStep.delegate.map { s =>
          if (s.intValue() > index + 1) GlyphIcon.Completed else step.icon
        }
      }
      val separator = new Rectangle {
        styleClass += "separator"
        width = 80
        height = 4
      }
      if (index < stepNumber - 1) Seq(icon, separator) else Seq(icon)
    }
  }

  private val cancelButton = new Button("Cancel") {
    onAction = { _: Event =>
      cancel()
      close()
    }
  }

  private val nextButton = new Button {
    text <== when(currentStep === stepNumber) choose "Finish" otherwise "Continue"
    disable = true
    handleEvent(ActionEvent.Action) { () =>
      if (currentStep.value < stepNumber) changeToStep(currentStep.value + 1) else close()
    }
  }

  private val wizardFooter = new HBox {
    styleClass += "footer"
    content = Seq(cancelButton, nextButton)
  }

  private val rootWizardPane: BorderPane = new BorderPane {
    styleClass += "wizard"
    top = wizardHeader
    center = steps.head
    bottom = wizardFooter
  }

  title = Wizard.this.wizardTitle
  resizable = false
  scene = new CoinffeineScene(additionalStyles: _*) {
    root = rootWizardPane
  }

  onCloseRequest = { _: Event => cancel() }

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

object Wizard {

  class CancelledByUser(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
}
