package coinffeine.model.currency

object Implicits {

  import scala.language.implicitConversions

  implicit val bitcoinIsNumeric = Bitcoin.numeric
  implicit val euroIsNumeric = Euro.numeric
  implicit val usDollarIsNumeric = UsDollar.numeric

  implicit class BitcoinSatoshiConverter(btc: Bitcoin.Amount) {
    def asSatoshi = (btc.value * Bitcoin.OneBtcInSatoshi).toBigIntExact().get.underlying()
  }

  class UnitImplicits(val i: BigDecimal) extends AnyVal {
    def BTC: Bitcoin.Amount = Bitcoin(i)

    def EUR: CurrencyAmount[Euro.type] = Euro(i)

    def USD: CurrencyAmount[UsDollar.type] = UsDollar(i)
  }

  implicit def pimpMyDouble(i: Double) = new UnitImplicits(i)

  implicit def pimpMyDecimal(i: BigDecimal) = new UnitImplicits(i)

  implicit def pimpMyInt(i: Int) = new UnitImplicits(BigDecimal(i))
}
