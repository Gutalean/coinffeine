package coinffeine.gui.control

import java.text.DecimalFormat
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.geometry.Pos
import scalafx.scene.control.Label
import scalafx.scene.layout.HBox

import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency.BitcoinAmount

class WalletBalanceWidget(balanceProperty: ReadOnlyObjectProperty[BitcoinAmount]) extends HBox {
  id = "wallet-widget"
  prefHeight = 26
  alignment = Pos.CENTER
  content = Seq(
    new Label("BTC") {
      prefWidth = 34
      prefHeight = 24
      styleClass = Seq("btc-label")
      alignment = Pos.CENTER
    },
    new Label(formatBalance(balanceProperty.value)) {
      prefWidth = 100
      id = "wallet-balance"
      alignment = Pos.CENTER_RIGHT
      text <== balanceProperty.delegate.map(formatBalance)
    }
  )

  private def formatBalance(balance: BitcoinAmount): String =
    WalletBalanceWidget.BalanceFormat.format(balance.value)
}

object WalletBalanceWidget {
  private val BalanceFormat = new DecimalFormat("###,###,##0.00######")
}
