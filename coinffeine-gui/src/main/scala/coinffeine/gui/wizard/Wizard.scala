package coinffeine.gui.wizard

import scalafx.Includes._
import scalafx.beans.property.{BooleanProperty, IntegerProperty, ObjectProperty}
import scalafx.event.{ActionEvent, Event}
import scalafx.scene.control.Button
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.scene.shape.Rectangle
import scalafx.stage.{Modality, Stage, StageStyle, Window}

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.{GlyphIcon, GlyphLabel}
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.NodeStyles.HExpand

/** Step-by-step wizard that accumulates information of type Data.
  *
  * @param steps        Sequence of wizard steps
  * @param data         The data configured by the wizard
  * @param wizardTitle  Wizard title
  * @tparam Data        Type of the wizard result
  */
class Wizard[Data](steps: Seq[StepPane[Data]],
                   data: Data,
                   wizardTitle: String = "",
                   additionalStyles: Seq[String] = Seq.empty) extends Stage(StageStyle.UTILITY) {

  private val cancelled = new BooleanProperty(this, "cancelled", false)
  private val stepNumber = steps.size
  private val currentStep = new IntegerProperty(this, "currentStep", 1)
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
    currentStepPane <== currentStep.delegate.map(c => steps(c.intValue() - 1))
    rootWizardPane.center <== currentStepPane.delegate.map(_.delegate)
    nextButton.disable <== currentStepPane.delegate.flatMap(_.canContinue.not())
    changeToStep(1)
  }

  private def changeToStep(index: Int): Unit = {
    currentStep.value = index
    val pane = currentStepPane.value
    Option(pane.onActivation.value).foreach(_.handle(new StepPaneEvent(this, pane)))
  }
}

object Wizard {

  class CancelledByUser(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)
}
