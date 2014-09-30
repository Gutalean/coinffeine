package coinffeine.model.currency

import java.util.{Currency => JavaCurrency}

case object Euro extends FiatCurrency {
  val javaCurrency = JavaCurrency.getInstance("EUR")
  override val precision = 2
}
