package coinffeine.gui.application.stats

import scalafx.scene.layout.{Pane, StackPane}

import coinffeine.gui.application.ApplicationView
import coinffeine.model.currency.Euro
import coinffeine.model.market.Market
import coinffeine.peer.api.CoinffeineApp

class StatsView(app: CoinffeineApp) extends ApplicationView {

  override val name = "Stats"

  private val orderBookChart = new OrderBookChart(app.marketStats, Market(Euro))

  override def centerPane = new StackPane {
    id = "stats-center-pane"
    content = orderBookChart
  }

  // TODO: provide a valid control pane
  override def controlPane: Pane = new Pane
}
