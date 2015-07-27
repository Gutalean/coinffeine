package coinffeine.gui.control

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.Label

import coinffeine.gui.beans.Implicits._

/** Label using typography to render icons */
class GlyphLabel extends Label {

  private val _icon = new ObjectProperty[GlyphIcon](this, "icon", GlyphIcon.Coinffeine)
  def icon: ObjectProperty[GlyphIcon] = _icon
  def icon_=(value: GlyphIcon): Unit = {
    _icon.value = value
  }

  styleClass += "glyph-icon"
  text <== _icon.delegate.map(_.letter.toString).toStr
}
