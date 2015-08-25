package coinffeine.gui.control

import java.util.Locale
import scalafx.Includes._
import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.scene.control.ComboBox
import scalafx.scene.input.KeyEvent
import scalafx.util.StringConverter

import coinffeine.gui.beans.Implicits._
import coinffeine.model.currency.FiatCurrency

class FiatCurrencyChooser(initialCurrency: Option[FiatCurrency] = None)
    extends ComboBox(FiatCurrencyChooser.Items) {

  import FiatCurrencyChooser._

  converter = Formatter
  initialCurrency.foreach(value = _)

  def currency: ReadOnlyObjectProperty[Option[FiatCurrency]] = {
    val prop = new ObjectProperty[Option[FiatCurrency]](this, "currency", None)
    prop <== value.delegate.map(Option.apply)
    prop
  }

  onKeyTyped = (event: KeyEvent) => {
    val char = event.character.toLowerCase
    FiatCurrencyChooser.Items
      .find(currency => Formatter.toString(currency).toLowerCase.startsWith(char))
      .foreach { currency => value = currency }
  }

}

private object FiatCurrencyChooser {

  object Formatter extends StringConverter[FiatCurrency]() {

    override def fromString(currencyDescription: String): FiatCurrency =
      throw new UnsupportedOperationException()

    override def toString(currency: FiatCurrency): String = "%s (%s)".format(
      currency.javaCurrency.getDisplayName(Locale.US),
      currency.symbol
    )
  }

  val Items = FiatCurrency.supported.toSeq.sortBy(Formatter.toString)
}
