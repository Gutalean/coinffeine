package coinffeine.gui.application.stats

import scalafx.Includes
import scalafx.scene.chart.{XYChart, NumberAxis, AreaChart}

class OrderBookChart extends AreaChart[Number, Number](
    xAxis = new NumberAxis("Price", 0, 1000, 10),
    yAxis = new NumberAxis("Bitcoins", 0, 1000, 10)) with Includes {

  title = "Order Book"

  XAxis.setAutoRanging(true)
  YAxis.setAutoRanging(true)

  /* Some fake data */
  val bidSeries = new XYChart.Series[Number, Number]() {
    name = "Bid"
    data = Seq(
      (100.0, 1000.0),
      (150.0, 869.0),
      (200.0, 646.0),
      (250.0, 353.0),
      (300.0, 154.0),
      (350.0, 40.0),
      (400.0, 3.0)).map(toChartData)
  }

  val askSeries = new XYChart.Series[Number, Number]() {
    name = "Ask"
    data = Seq(
      (401.0, 3.0),
      (450.0, 62.0),
      (500.0, 123.0),
      (550.0, 459.0),
      (600.0, 646.0),
      (650.0, 845.0),
      (700.0, 1000.0)).map(toChartData)
  }

  data() += bidSeries
  data() += askSeries

  private def toChartData(data: (Double, Double)) = XYChart.Data[Number, Number](data._1, data._2)
}
