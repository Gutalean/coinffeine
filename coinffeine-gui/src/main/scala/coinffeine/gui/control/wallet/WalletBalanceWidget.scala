package coinffeine.gui.control.wallet

import java.text.DecimalFormat
import scalafx.Includes._
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.geometry.Pos
import scalafx.scene.control.Label
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout.HBox

import org.controlsfx.control.PopOver

import coinffeine.gui.control.PopUpContent
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency.{Balance, Currency}

abstract class WalletBalanceWidget[C <: Currency, B <: Balance[C]](
    currency: C,
    balanceProperty: ReadOnlyObjectProperty[Option[B]]) extends HBox {

  this: PopUpContent =>

  private val symbol = currency.toString
  private val popup = {
    val pop = popupContent.fold(new PopOver())(new PopOver(_))
    pop.arrowLocationProperty().setValue(PopOver.ArrowLocation.TOP_CENTER)
    pop
  }

  id = "balance-widget"
  prefHeight = 26
  alignment = Pos.Center
  content = Seq(
    new Label(symbol) {
      prefWidth = 34
      prefHeight = 24
      styleClass = Seq("currency-label", s"$symbol-label")
      alignment = Pos.Center
    },
    new Label(formatBalance(balanceProperty.value)) {
      prefWidth = 100
      prefHeight = 24
      id = s"$symbol-balance"
      styleClass = Seq("balance")
      alignment = Pos.CenterRight
      text <== balanceProperty.delegate.map(formatBalance)
    }
  )

  balanceProperty.onChange(updateClasses())
  updateClasses()
  configMouseHandlers()

  private def configMouseHandlers(): Unit = {
    if (popupContent.isDefined) {
      onMouseEntered = { event: MouseEvent =>
        val pos = this.localToScreen(this.getWidth * 0.5, this.getHeight * 1.5)
        popup.show(this, pos.getX, pos.getY)
      }
      onMouseExited = { event: MouseEvent =>
        popup.hide()
      }
    }
  }

  private def updateClasses(): Unit = {
    val nonCurrent = balanceProperty.value.fold(true)(_.hasExpired)
    styleClass.clear()
    if (nonCurrent) {
      styleClass.add("non-current")
    }
  }

  private def formatBalance(balanceOpt: Option[B]): String =
    balanceOpt.fold("-.--") { balance =>
      WalletBalanceWidget.BalanceFormat.format(balance.amount.value)
    }
}

object WalletBalanceWidget {
  private val BalanceFormat = new DecimalFormat("###,###,##0.00######")
}
