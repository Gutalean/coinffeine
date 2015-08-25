package coinffeine.gui.application.stats

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.util.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalafx.Includes
import scalafx.beans.property.ReadOnlyObjectProperty
import scalafx.scene.chart.{AreaChart, NumberAxis, XYChart}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.beans.Implicits._
import coinffeine.gui.util.FxExecutor
import coinffeine.model.market._
import coinffeine.model.order.{Ask, Bid, OrderType}
import coinffeine.peer.api.MarketStats

class OrderBookChart(stats: MarketStats, market: ReadOnlyObjectProperty[Market]) extends AreaChart[Number, Number](
    OrderBookChart.xAxis(market), OrderBookChart.yAxis()) with Includes with LazyLogging {

  private val reloader = new Timeline(new KeyFrame(
    Duration.millis(OrderBookChart.UpdateInterval.toMillis), new EventHandler[ActionEvent] {
      override def handle(event: ActionEvent) = reloadData()
    }))

  startDataReload()

  private def startDataReload(): Unit = {
    reloadData()
    reloader.setCycleCount(Animation.INDEFINITE)
    reloader.play()
  }

  private def reloadData(): Unit = {
    implicit val executor = FxExecutor.asContext
    logger.debug("Reloading order book chart data... ")
    stats.openOrders(market.value).onComplete {
      case Success(entries) =>
        data().clear()
        data() += toSeries(entries, Bid)
        data() += toSeries(entries, Ask)
        logger.debug("Order book chart data successfully reloaded")
      case Failure(e) =>
        logger.error("Failed to reload order book chart data", e)
    }
  }

  private def toSeries(data: Set[OrderBookEntry], orderType: OrderType) = {
    val series = new XYChart.Series[Number, Number]() {
      name = orderType.toString
    }
    val seriesData = data.toSeq
      .filter(entry => entry.orderType == orderType && entry.price.isLimited)
      .groupBy(_.price.toOption.get.value.toDouble)
      .mapValues(sumCurrencyAmount)
    series.data = seriesData.toSeq.sortBy(_._1).map(toChartData)
    series
  }

  private def sumCurrencyAmount(entries: Seq[OrderBookEntry]) =
    entries.map(_.amount.value.toDouble).sum

  private def toChartData(data: (Double, Double)) = XYChart.Data[Number, Number](data._1, data._2)
}

object OrderBookChart {

  val UpdateInterval = 20.seconds

  private def xAxis(market: ReadOnlyObjectProperty[Market]) = {
    val axis = new NumberAxis {
      autoRanging = true
      forceZeroInRange = false
    }
    axis.label <== market.delegate.map(m => s"Price (${m.currency}/BTC)").toStr
    axis.tickLabelFormatter <== market.delegate.map(m =>
      NumberAxis.DefaultFormatter(axis, "", m.currency.javaCurrency.getSymbol).delegate)
    axis
  }

  private def yAxis() = new NumberAxis {
    autoRanging = true
    label = s"Bitcoins"
    tickLabelFormatter = NumberAxis.DefaultFormatter(this, "", "BTC")
  }
}
