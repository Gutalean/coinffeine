package coinffeine.common.config

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.config.{Config, ConfigException, ConfigValue}

class PimpTypesafeConfig(val config: Config) extends AnyVal {

  def getBooleanOpt(key: String): Option[Boolean] = getOptional(config.getBoolean(key))

  def getIntOpt(key: String): Option[Int] = getOptional(config.getInt(key))

  def getStringOpt(key: String): Option[String] = getOptional(config.getString(key))

  def getNonEmptyStringOpt(key: String): Option[String] =
    getOptional(config.getString(key)).filter(_.nonEmpty)

  def getSeconds(key: String): FiniteDuration = config.getDuration(key, TimeUnit.SECONDS).seconds

  def getSecondsOpt(key: String): Option[FiniteDuration] =
    getOptional(config.getDuration(key, TimeUnit.SECONDS).seconds)

  private def getOptional[T](extractor: => T): Option[T] =
    try Some(extractor)
    catch { case _: ConfigException.Missing => None }

  def withOptValue(key: String, maybeValue: Option[ConfigValue]): Config =
    maybeValue.fold(config) { value =>
      config.withValue(key, value)
    }
}
