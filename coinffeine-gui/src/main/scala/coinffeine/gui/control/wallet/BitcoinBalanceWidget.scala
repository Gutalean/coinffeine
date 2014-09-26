package coinffeine.gui.control.wallet

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.geometry.HPos
import scalafx.scene.control.Label
import scalafx.scene.layout.{VBox, ColumnConstraints, GridPane, HBox}

import coinffeine.gui.control.PopUpContent
import coinffeine.gui.util.ScalafxImplicits._
import coinffeine.model.currency.BitcoinBalance
import coinffeine.model.currency.Currency.Bitcoin

class BitcoinBalanceWidget(balanceProperty: ReadOnlyObjectProperty[Option[BitcoinBalance]])
  extends WalletBalanceWidget(Bitcoin, balanceProperty) with PopUpContent {

  protected override lazy val popupContent = {
    val content = new VBox() {
      id = "bitcoin-balance-popup"
      content = Seq(
        new Label("Your bitcoin balance") { styleClass = Seq("popup-title") },
        new GridPane() {
          styleClass = Seq("popup-balance-summary")
          columnConstraints = Seq(
            new ColumnConstraints() { halignment = HPos.RIGHT },
            new ColumnConstraints() { halignment = HPos.LEFT }
          )
          val lines: Seq[(String, BitcoinBalance => String)] = Seq(
            "Active:" -> (_.estimated.toString),
            "Available:" -> (_.available.toString),
            "In use:" -> (_.blocked.toString),
            "Min output:" -> (_.minOutput.map(_.toString).getOrElse("None"))
          )

          lines.zipWithIndex.foreach { case ((title, valueExtractor), index) =>
            add(new Label(title), 0, index)
            add(new Label() {
              text <== balanceProperty.delegate.map {
                case Some(balance) => valueExtractor(balance)
                case None => "unknown"
              }
            }, 1, index)
          }
        }
      )
    }
    Some(content)
  }
}

