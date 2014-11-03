package coinffeine.gui.control.wallet

import java.text.DecimalFormat
import scalafx.Includes._
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.geometry.Pos
import scalafx.scene.control.Label
import scalafx.scene.image.{Image, ImageView}
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

  styleClass += "balance-widget"
  prefHeight = 26
  alignment = Pos.Center
  content = Seq(
    new Label(symbol) {
      prefWidth = 34
      prefHeight = 24
      styleClass = Seq("currency-label", s"$symbol-label")
      alignment = Pos.Center
    },
    new HBox(spacing = 4) {
      id = s"$symbol-balance"
      styleClass = Seq("balance")
      content = Seq(
        new ImageView(new Image("/graphics/warning.png")) {
          styleClass += "balance-warning"
          alignment = Pos.Center
          visible <== balanceProperty.delegate.mapToBool(isCurrent)
        },
        new Label(formatBalance(balanceProperty.value)) {
          prefWidth = 100
          prefHeight = 24
          alignment = Pos.CenterRight
          text <== balanceProperty.delegate.map(formatBalance)
        }
      )
    }
  )

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

  private def isCurrent(maybeBalance: Option[Balance[C]]): Boolean =
    maybeBalance.fold(true)(_.hasExpired)

  private def formatBalance(balanceOpt: Option[B]): String =
    balanceOpt.fold("-.--") { balance =>
      WalletBalanceWidget.BalanceFormat.format(balance.amount.value)
    }
}

object WalletBalanceWidget {
  private val BalanceFormat = new DecimalFormat("###,###,##0.00######")
}
