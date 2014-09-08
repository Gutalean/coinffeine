package coinffeine.gui.wizard

import javafx.collections.ObservableList
import scalafx.beans.property.IntegerProperty
import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.{HBox, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Circle

/** A pane with a dot per step. The dot matching with the current step has a different color. */
private[wizard] class ProgressIndicator(steps: Int, currentStep: IntegerProperty) {

  val pane = new HBox(spacing = 5) {
    content = for (index <- 1 to steps) yield stepIndicator(index)
  }

  private def stepIndicator(step: Int): Node = new StackPane() {
    content = Seq(
      new Circle { radius = 10 },
      new Label(s"$step")
    )
    currentStep.onChange {
      setStepStyle(step, styleClass)
    }
  }

  private def setStepStyle(index: Int, styles: ObservableList[String]): Unit = {
    styles.removeAll("stepIndexActive", "stepIndexInactive")
    styles.add(if (currentStep.value == index) "stepIndexActive" else "stepIndexInactive")
  }
}

private[wizard] object ProgressIndicator {
  private val SelectedColor = Color.RED
  private val UnselectedColor = Color.web("#afc9e1")
}

