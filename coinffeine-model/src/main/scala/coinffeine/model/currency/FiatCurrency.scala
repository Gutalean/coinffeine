package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

object FiatCurrency {

  def apply(currencyCode: String): FiatCurrency = apply(JavaCurrency.getInstance(currencyCode))

  def apply(javaCurrency: JavaCurrency): FiatCurrency = javaCurrency match {
    case UsDollar.javaCurrency => UsDollar
    case Euro.javaCurrency => Euro
    case _ => throw new IllegalArgumentException(
      s"cannot convert $javaCurrency into a known Coinffeine fiat currency")
  }

  val values: Set[FiatCurrency] = Set(Euro, UsDollar)
}

/** A fiat currency. */
trait FiatCurrency extends Currency {
  val javaCurrency: JavaCurrency
  override lazy val preferredSymbolPosition = Currency.SymbolSuffixed
  override lazy val symbol = javaCurrency.getCurrencyCode
  override lazy val toString = symbol
}
