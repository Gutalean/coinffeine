package coinffeine.gui.application.stats

import scalafx.scene.control.{Label, ScrollPane}
import scalafx.scene.layout.{HBox, Pane, VBox}

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.scene.styles.{NodeStyles, PaneStyles}
import coinffeine.model.currency.Euro
import coinffeine.model.market.Market
import coinffeine.peer.api.CoinffeineApp

class StatsView(app: CoinffeineApp) extends ApplicationView {

  override val name = "Stats"

  private val orderBookChart = new OrderBookChart(app.marketStats, Market(Euro))

  override def centerPane = new VBox {
    id = "stats-center-pane"
    content = Seq(
      new HBox with PaneStyles.Centered {
        styleClass += "header"
        content = new Label("ORDER BOOK")
      },
      new ScrollPane() with NodeStyles.VExpand { content = orderBookChart }
    )
  }

  // TODO: provide a valid control pane
  override def controlPane: Pane = new Pane
}
