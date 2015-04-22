package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

case object UsDollar extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("USD")
  override val precision = 2
}
