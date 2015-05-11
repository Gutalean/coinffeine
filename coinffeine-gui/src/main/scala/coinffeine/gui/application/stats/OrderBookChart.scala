package coinffeine.gui.application.stats

import javafx.animation.{Animation, KeyFrame, Timeline}
import javafx.event.{ActionEvent, EventHandler}
import javafx.util.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalafx.Includes
import scalafx.scene.chart.{AreaChart, NumberAxis, XYChart}

import com.typesafe.scalalogging.LazyLogging

import coinffeine.gui.util.FxExecutor
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.market._
import coinffeine.model.order.{Ask, Bid, OrderType}
import coinffeine.peer.api.MarketStats

class OrderBookChart[C <: FiatCurrency](stats: MarketStats,
                                        market: Market[C]) extends AreaChart[Number, Number](
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
    stats.openOrders(market).onComplete {
      case Success(entries) =>
        data().clear()
        data() += toSeries(entries, Bid)
        data() += toSeries(entries, Ask)
        logger.debug("Order book chart data successfully reloaded")
      case Failure(e) =>
        logger.error("Failed to reload order book chart data", e)
    }
  }

  private def toSeries(data: Set[OrderBookEntry[C]], orderType: OrderType) = {
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

  private def sumCurrencyAmount(entries: Seq[OrderBookEntry[C]]) =
    entries.map(_.amount.value.toDouble).sum

  private def toChartData(data: (Double, Double)) = XYChart.Data[Number, Number](data._1, data._2)
}

object OrderBookChart {

  val UpdateInterval = 20.seconds

  private def xAxis[C <: FiatCurrency](market: Market[C]) = new NumberAxis {
    autoRanging = true
    forceZeroInRange = false
    label = s"Price (${market.currency}/BTC)"
    tickLabelFormatter = NumberAxis.DefaultFormatter(
      this, "", market.currency.javaCurrency.getSymbol)
  }

  private def yAxis[C <: FiatCurrency]() = new NumberAxis {
    autoRanging = true
    label = s"Bitcoins"
    tickLabelFormatter = NumberAxis.DefaultFormatter(this, "", "BTC")
  }
}
