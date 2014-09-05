package coinffeine.model.currency

import coinffeine.model.currency.Currency._

object Implicits {

  import scala.language.implicitConversions

  implicit class BitcoinSatoshiConverter(btc: BitcoinAmount) {
    def asSatoshi = (btc.value * Bitcoin.OneBtcInSatoshi).toBigIntExact().get.underlying()
  }

  class UnitImplicits(val i: BigDecimal) extends AnyVal {
    def BTC: BitcoinAmount = Bitcoin(i)

    def EUR: CurrencyAmount[Euro.type] = Euro(i)

    def USD: CurrencyAmount[UsDollar.type] = UsDollar(i)
  }

  implicit def pimpMyDouble(i: Double) = new UnitImplicits(i)

  implicit def pimpMyDecimal(i: BigDecimal) = new UnitImplicits(i)

  implicit def pimpMyInt(i: Int) = new UnitImplicits(BigDecimal(i))
}
