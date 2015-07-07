package coinffeine.peer.config

import scala.collection.JavaConverters._
import scalaz.syntax.std.boolean._

import com.typesafe.config.{Config, ConfigFactory}

import coinffeine.model.currency.{FiatAmounts, FiatCurrency}

object FiatAmountsMapping {

  def toConfig(amounts: FiatAmounts, path: String, config: Config): Config = {
    toConfig(amounts).atPath(path).withFallback(config)
  }

  def toConfig(amounts: FiatAmounts): Config = {
    val valuesByCurrencyCode = amounts.amounts.map { amount =>
      amount.currency.toString -> amount.value.toDouble
    }.toMap
    ConfigFactory.parseMap(valuesByCurrencyCode.asJava)
  }

  def fromConfig(path: String, config: Config): Option[FiatAmounts] =
    config.hasPath(path).option(fromConfig(config.getConfig(path)))

  def fromConfig(config: Config): FiatAmounts = FiatAmounts(
    for (key <- config.root().keySet().asScala.toList)
    yield FiatCurrency(key)(config.getDouble(key))
  )
}
