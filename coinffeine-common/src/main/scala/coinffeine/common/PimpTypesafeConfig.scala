package coinffeine.common

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import com.typesafe.config.{ConfigException, Config}

class PimpTypesafeConfig(val config: Config) extends AnyVal {

  def getBooleanOpt(key: String): Option[Boolean] = getOptional(_.getBoolean(key))

  def getStringOpt(key: String): Option[String] = getOptional(_.getString(key))

  def getNonEmptyStringOpt(key: String): Option[String] = getOptional { cfg =>
    val s = cfg.getString(key)
    if (s.isEmpty)
      throw new ConfigException.Missing(s"a non-empty string was expected for key $key")
    else s
  }

  def getSecondsOpt(key: String): Option[FiniteDuration] =
    getOptional(_.getDuration(key, TimeUnit.SECONDS).seconds)

  private def getOptional[T](extractor: Config => T): Option[T] =
    try { Some(extractor(config)) }
    catch { case _: ConfigException.Missing => None }
}
