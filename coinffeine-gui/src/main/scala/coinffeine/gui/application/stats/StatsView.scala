package coinffeine.gui.application.stats

import scalafx.scene.layout.Pane

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.pane.PagePane
import coinffeine.model.currency.Euro
import coinffeine.model.market.Market
import coinffeine.peer.api.CoinffeineApp

class StatsView(app: CoinffeineApp) extends ApplicationView {

  override val name = "Stats"

  private val orderBookChart = new OrderBookChart(app.marketStats, Market(Euro))

  override def centerPane = new PagePane {
    id = "stats-center-pane"
    headerText = "ORDER BOOK"
    pageContent = orderBookChart
  }

  override def controlPane = new Pane
}
