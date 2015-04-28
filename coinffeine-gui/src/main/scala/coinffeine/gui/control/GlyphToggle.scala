package coinffeine.gui.control

import scalafx.beans.property.{StringProperty, ObjectProperty}
import scalafx.css.PseudoClass
import scalafx.scene.control.{Label, ToggleGroup, ToggleButton}
import scalafx.scene.layout.VBox

import coinffeine.gui.beans.Implicits._

/** A toggle button that used typography to render. */
class GlyphToggle(initialText: String = "") extends VBox {

  styleClass += "glyph-toggle"

  private val _icon = new ObjectProperty[GlyphIcon](this, "icon", GlyphIcon.Coinffeine)
  def icon: ObjectProperty[GlyphIcon] = _icon
  def icon_=(value: GlyphIcon): Unit = {
    _icon.value = value
  }

  private val _text = new StringProperty(this, "text", initialText)
  def text: StringProperty = _text
  def text_=(value: String): Unit = { _text.value = value }

  private val _toggleGroup = new ObjectProperty[ToggleGroup](this, "toggleGroup", new ToggleGroup())
  def toggleGroup: ObjectProperty[ToggleGroup] = _toggleGroup
  def toggleGroup_=(value: ToggleGroup): Unit = { _toggleGroup.value = value }

  val toggle = new ToggleButton {
    styleClass += "glyph-icon"
    toggleGroup <== _toggleGroup.delegate.map(_.delegate)
    text <== _icon.delegate.mapToString(_.letter.toString)
  }

  val label = new Label { text <== _text }

  content = Seq(toggle, label)

  toggle.selected.onChange { (_, _, newValue) =>
    delegate.pseudoClassStateChanged(PseudoClass("selected"), newValue)
  }
}
