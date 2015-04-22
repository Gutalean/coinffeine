package coinffeine.gui.wizard

import javafx.event.EventHandler
import scala.language.implicitConversions
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.layout.StackPane

import coinffeine.gui.control.GlyphIcon

/** An step of a wizard */
trait StepPane[Data] extends StackPane {
  def icon: GlyphIcon

  val canContinue: BooleanProperty = new BooleanProperty(this, "canContinue", true)

  private val _onActivation: ObjectProperty[EventHandler[StepPaneEvent]] =
    new ObjectProperty(this, "onActivation")

  def onActivation: ObjectProperty[EventHandler[StepPaneEvent]] = _onActivation

  def onActivation_=(value: EventHandler[StepPaneEvent]): Unit = { _onActivation.value = value }

  implicit def stepPaneEventWrapper(handler: StepPaneEvent => Any): EventHandler[StepPaneEvent] =
    new EventHandler[StepPaneEvent] {
      override def handle(event: StepPaneEvent): Unit = { handler(event) }
    }
}
