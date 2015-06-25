package coinffeine.gui.application.wallet

import scalafx.scene.control.Label
import scalafx.scene.layout.{GridPane, HBox, VBox}
import scalafx.stage.{Modality, Stage, StageStyle}
import scalaz.syntax.std.option._

import coinffeine.gui.application.properties.WalletProperties
import coinffeine.gui.beans.Implicits._
import coinffeine.gui.control.SupportWidget
import coinffeine.gui.scene.CoinffeineScene
import coinffeine.gui.scene.styles.{Stylesheets, TextStyles}
import coinffeine.model.currency.{BitcoinAmount, BitcoinBalance, Currency}

class FundsDialog(props: WalletProperties) {

  private val dialogScene = new CoinffeineScene(Stylesheets.Wallet) {
    root = new VBox {
      styleClass += "wallet-funds"
      children = Seq(
        new HBox {
          styleClass += "header"
          children = Seq(
            new Label("Wallet "),
            new Label("Funds") with TextStyles.Emphasis,
            new SupportWidget("funds-dialog")
          )
        },
        new GridPane {
          styleClass += "funds-table"
          addRow(0, makeAmountLabel("ACTIVE", "BALANCE"), makeAmount(_.estimated.some))
          addRow(1, makeAmountLabel("AVAILABLE", "BALANCE"), makeAmount(_.available.some))
          addRow(2, makeAmountLabel("FUNDS", "IN USE"), makeAmount(_.blocked.some))
          addRow(3, makeAmountLabel("MINIMUM", "OUTPUT"), makeAmount(_.minOutput))
        }
      )
    }
  }

  private def makeAmountLabel(normalText: String, emphasizedText: String) = new HBox {
    styleClass += "amount-label"
    children = Seq(
      new Label(normalText + ' '),
      new Label(emphasizedText + ':') with TextStyles.Emphasis
    )
  }

  private def makeAmount(selector: BitcoinBalance => Option[BitcoinAmount]) = new HBox {
    styleClass += "amount"
    children = Seq(
      new Label {
        text <== props.balance.delegate.mapToString { maybeBalance =>
          maybeBalance.flatMap(selector).fold("__.________")(_.format(Currency.NoSymbol))
        }
      },
      new Label("BTC") with TextStyles.Emphasis
    )
  }

  private val dialogStage = new Stage(style = StageStyle.UTILITY) {
    title = "Wallet funds"
    initModality(Modality.APPLICATION_MODAL)
    scene = dialogScene
  }

  def show(): Unit = {
    dialogStage.centerOnScreen()
    dialogStage.resizable = false
    dialogStage.showAndWait()
  }
}
