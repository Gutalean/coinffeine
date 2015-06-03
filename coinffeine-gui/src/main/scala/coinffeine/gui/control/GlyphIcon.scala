package coinffeine.gui.control

trait GlyphIcon {
  val letter: Char
}

object GlyphIcon {
  case object Coinffeine extends GlyphIcon { override val letter = 'q' }
  case object MagnifyingGlass extends GlyphIcon { override val letter = 'w' }
  case object Cross extends GlyphIcon { override val letter = 'e' }
  case object Mark extends GlyphIcon { override val letter = 'r' }
  case object Support extends GlyphIcon { override val letter = 't' }
  case object Buy extends GlyphIcon { override val letter = 'y' }
  case object Sell extends GlyphIcon { override val letter = 'u' }
  case object ExchangeTypes extends GlyphIcon { override val letter = 'i' }
  case object MarketPrice extends GlyphIcon { override val letter = 'o' }
  case object Network extends GlyphIcon { override val letter = 'p' }
  case object Completed extends GlyphIcon { override val letter = 'a' }
  case object BitcoinInflow extends GlyphIcon { override val letter = 's' }
  case object BitcoinOutflow extends GlyphIcon { override val letter = 'd' }
  case object OkPay extends GlyphIcon { override val letter = 'f' }
  case object Visa extends GlyphIcon { override val letter = 'g' }
  case object Paypal extends GlyphIcon { override val letter = 'h' }
  case object Dwolla extends GlyphIcon { override val letter = 'j' }
  case object Printer extends GlyphIcon { override val letter = 'k' }
  case object LoudSpeaker extends GlyphIcon { override val letter = 'l' }
  case object Number1 extends GlyphIcon { override val letter = 'z' }
  case object Number2 extends GlyphIcon { override val letter = 'x' }
  case object Number3 extends GlyphIcon { override val letter = 'c' }
  case object Warning extends GlyphIcon { override val letter = 'v' }
  case object Number4 extends GlyphIcon { override val letter = 'b' }

  val Numbers = Map(
    1 -> Number1,
    2 -> Number2,
    3 -> Number3,
    4 -> Number4
  )
}
