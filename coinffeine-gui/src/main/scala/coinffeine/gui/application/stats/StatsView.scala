package coinffeine.gui.application.stats

import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.layout.Pane

import coinffeine.gui.application.ApplicationView
import coinffeine.gui.pane.PagePane
import coinffeine.model.market.Market
import coinffeine.peer.api.CoinffeineApp

class StatsView(app: CoinffeineApp, market: ReadOnlyObjectProperty[Market]) extends ApplicationView {

  override val name = "Stats"

  private val orderBookChart = new OrderBookChart(app.marketStats, market)

  override def centerPane = new PagePane {
    id = "stats-center-pane"
    headerText = "ORDER BOOK"
    pageContent = orderBookChart
  }

  override def controlPane = new Pane
}
