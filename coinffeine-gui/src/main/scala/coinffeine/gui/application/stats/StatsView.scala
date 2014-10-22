package coinffeine.gui.application.stats

import scalafx.scene.chart.{AreaChart, XYChart}
import scalafx.scene.layout.StackPane

import coinffeine.gui.application.ApplicationView

class StatsView extends ApplicationView {

  override val name = "Stats"

  private val orderBookChart = new OrderBookChart

  override def centerPane = new StackPane {
    id = "stats-center-pane"
    content = orderBookChart
  }
}
