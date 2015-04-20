package coinffeine.gui.control

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.{ToggleGroup, ToggleButton}

import coinffeine.gui.beans.Implicits._

/** A toggle button that used typography to render. */
class GlyphToggle(group: ToggleGroup) extends ToggleButton {

  private val _icon = new ObjectProperty[GlyphIcon](this, "icon", GlyphIcon.Coinffeine)
  def icon: ObjectProperty[GlyphIcon] = _icon
  def icon_=(value: GlyphIcon): Unit = {
    _icon.value = value
  }

  styleClass ++= Seq("glyph-icon", "glyph-toggle")
  toggleGroup = group
  text <== _icon.delegate.mapToString(_.letter.toString)
}
