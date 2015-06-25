package coinffeine.model.currency

object CurrencyAmountFormatter {

  def format(amount: CurrencyAmount[_], symbolPos: Currency.SymbolPosition): String = {
    val currency = amount.currency
    val units = amount.units
    val numbers = {
      val formatString = s"%s%d.%0${currency.precision}d"
      formatString.format(
        if (units < 0) "-" else "",
        units.abs / currency.unitsInOne,
        units.abs % currency.unitsInOne)
    }
    addSymbol(numbers, symbolPos, currency)
  }

  def format(amount: CurrencyAmount[_]): String =
    format(amount, amount.currency.preferredSymbolPosition)

  def formatMissing[C <: Currency](currency: C, symbolPos: Currency.SymbolPosition): String = {
    val amount = "_." + "_" * currency.precision
    addSymbol(amount, symbolPos, currency)
  }

  def formatMissing[C <: Currency](currency: C): String =
    formatMissing(currency, currency.preferredSymbolPosition)

  private def addSymbol[C <: Currency](
      amount: String, symbolPos: Currency.SymbolPosition, currency: C): String =
    symbolPos match {
      case Currency.SymbolPrefixed => s"${currency.symbol}$amount"
      case Currency.SymbolSuffixed => s"$amount${currency.symbol}"
      case Currency.NoSymbol => amount
    }
}
