package coinffeine.gui.control

import scalafx.beans.property.ObjectProperty
import scalafx.scene.control.Label

import coinffeine.gui.beans.Implicits._

/** Label using typography to render icons */
class GlyphLabel extends Label {
  import GlyphLabel._

  private val _icon = new ObjectProperty[Icon](this, "icon", Icon.Coinffeine)
  def icon: ObjectProperty[Icon] = _icon
  def icon_=(value: Icon): Unit = {
    _icon.value = value
  }

  styleClass += "glyph-icon"
  text <== _icon.delegate.mapToString(_.letter.toString)
}

object GlyphLabel {

  sealed trait Icon {
    val letter: Char
  }

  object Icon {
    case object Coinffeine extends Icon { override val letter = 'q' }
    case object MagnifyingGlass extends Icon { override val letter = 'w' }
    case object Cross extends Icon { override val letter = 'e' }
    case object Mark extends Icon { override val letter = 'r' }
    case object Support extends Icon { override val letter = 't' }
    case object Buy extends Icon { override val letter = 'y' }
    case object Sell extends Icon { override val letter = 'u' }
    case object ExchangeTypes extends Icon { override val letter = 'i' }
    case object MarketPrice extends Icon { override val letter = 'o' }
    case object Network extends Icon { override val letter = 'p' }
    case object Completed extends Icon { override val letter = 'a' }
    case object BitcoinInflow extends Icon { override val letter = 's' }
    case object BitcoinOutflow extends Icon { override val letter = 'd' }
    case object OkPay extends Icon { override val letter = 'f' }
    case object Visa extends Icon { override val letter = 'g' }
    case object Paypal extends Icon { override val letter = 'h' }
    case object Dwolla extends Icon { override val letter = 'j' }
    case object Printer extends Icon { override val letter = 'k' }
    case object LoudSpeaker extends Icon { override val letter = 'l' }
  }
}
