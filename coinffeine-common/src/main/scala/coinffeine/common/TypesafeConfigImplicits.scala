package coinffeine.common

import scala.language.implicitConversions

import com.typesafe.config.Config

trait TypesafeConfigImplicits {

  implicit def pimpTypesafeConfig(config: Config): PimpTypesafeConfig =
    new PimpTypesafeConfig(config)
}

object TypesafeConfigImplicits extends TypesafeConfigImplicits
