package coinffeine.gui.wizard

import scalafx.beans.property.BooleanProperty
import scalafx.scene.layout.StackPane

import coinffeine.gui.control.GlyphIcon

/** An step of a wizard */
trait StepPane[Data] extends StackPane {
  def bindTo(data: Data): Unit
  def icon: GlyphIcon
  val canContinue: BooleanProperty = new BooleanProperty(this, "canContinue", false)
}
