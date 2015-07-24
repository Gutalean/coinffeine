package coinffeine.headless.parsing

import scala.util.matching.Regex

import coinffeine.model.currency._
import coinffeine.model.order.{Price => AmountPrice}

object AmountsParser {

  object BitcoinAmount {
    private val Pattern = """(?i)(\d+(?:\.\d{1,8})?)(?:BTC)?""".r

    def unapply(text: String): Option[BitcoinAmount] = text match {
      case Pattern(decimalExpression) =>
        Some(Bitcoin.exactAmount(BigDecimal(decimalExpression)))
      case _ => None
    }
  }

  object FiatAmount {
    private val Pattern = """(?i)(\d+(?:\.\d+)?)(\w+)""".r

    def unapply(text: String): Option[FiatAmount] = for {
      amount <- parseAmount(Pattern, text)
      currency <- lookupFiatCurrency(amount.symbol)
      if withinPrecision(amount.value, currency.precision)
    } yield currency.exactAmount(amount.value)

    private def withinPrecision(value: BigDecimal, precision: Int): Boolean =
      (value * BigDecimal(10).pow(precision)).isWhole()
  }

  object Price {
    private val Pattern = """(?i)(\d+(?:\.\d+)?)(\w+)(?:/BTC)?""".r

    def unapply(text: String): Option[AmountPrice] = for {
      amount <- parseAmount(Pattern, text)
      currency <- lookupFiatCurrency(amount.symbol)
    } yield AmountPrice(amount.value, currency)
  }

  private case class QualifiedDecimalAmount(value: BigDecimal, symbol: String)

  private def parseAmount(pattern: Regex, text: String): Option[QualifiedDecimalAmount] = {
    text match {
      case pattern(decimalExpression, symbol) =>
        Some(QualifiedDecimalAmount(BigDecimal(decimalExpression), symbol))
      case _ => None
    }
  }

  private def lookupFiatCurrency(symbol: String): Option[FiatCurrency] =
    FiatCurrency.supported.find(_.toString.equalsIgnoreCase(symbol))
}
