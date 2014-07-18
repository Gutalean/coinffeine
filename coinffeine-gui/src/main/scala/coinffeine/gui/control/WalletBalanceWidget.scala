package coinffeine.gui.control

import java.text.DecimalFormat
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.geometry.Pos
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency.{Currency, CurrencyAmount}

class WalletBalanceWidget[C <: Currency](
    currency: C, balanceProperty: ReadOnlyObjectProperty[Option[CurrencyAmount[C]]]) extends HBox {
  val symbol = currency.toString
  id = "balance-widget"
  prefHeight = 26
  alignment = Pos.CENTER
  content = Seq(
    new Label(symbol) {
      prefWidth = 34
      prefHeight = 24
      styleClass = Seq("currency-label", s"$symbol-label")
      alignment = Pos.CENTER
    },
    new Label(formatBalance(balanceProperty.value)) {
      prefWidth = 100
      id = s"$symbol-balance"
      alignment = Pos.CENTER_RIGHT
      text <== balanceProperty.delegate.map(formatBalance)
    }
  )

  private def formatBalance(balanceOpt: Option[CurrencyAmount[C]]): String =
    balanceOpt.fold("-.--") { balance =>
      WalletBalanceWidget.BalanceFormat.format(balance.value)
    }
}

object WalletBalanceWidget {
  private val BalanceFormat = new DecimalFormat("###,###,##0.00######")
}
