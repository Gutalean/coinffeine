package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

object FiatCurrency {

  def apply(javaCurrency: JavaCurrency): FiatCurrency = javaCurrency match {
    case Currency.UsDollar.javaCurrency => Currency.UsDollar
    case Currency.Euro.javaCurrency => Currency.Euro
    case _ => throw new IllegalArgumentException(s"cannot convert $javaCurrency into a known Coinffeine fiat currency")
  }
}

/** A fiat currency. */
trait FiatCurrency extends Currency {
  val javaCurrency: JavaCurrency
  override lazy val toString = javaCurrency.getCurrencyCode
}
