package coinffeine.model.currency2

import java.util.{Currency => JavaCurrency}

trait FiatCurrency extends Currency {
  override type Amount = FiatAmount

  val javaCurrency: JavaCurrency
  override lazy val preferredSymbolPosition = Currency.SymbolSuffixed
  override lazy val symbol = javaCurrency.getCurrencyCode
  override lazy val toString = symbol

  override def fromUnits(units: Long) = FiatAmount(units, this)
}

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
