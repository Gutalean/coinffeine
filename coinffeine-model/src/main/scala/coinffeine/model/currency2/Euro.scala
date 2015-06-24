package coinffeine.model.currency2

import java.util.{Currency => JavaCurrency}

case object Euro extends FiatCurrency {
  override val javaCurrency = JavaCurrency.getInstance("EUR")
  override val precision = 2
}
