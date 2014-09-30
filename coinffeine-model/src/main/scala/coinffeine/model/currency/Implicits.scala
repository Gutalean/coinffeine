package coinffeine.model.currency

trait Implicits {

  import scala.language.implicitConversions

  implicit val bitcoinIsNumeric = Bitcoin.numeric
  implicit val euroIsNumeric = Euro.numeric
  implicit val usDollarIsNumeric = UsDollar.numeric

  implicit class BitcoinSatoshiConverter(btc: Bitcoin.Amount) {
    def asSatoshi = (btc.value * Bitcoin.OneBtcInSatoshi).toBigIntExact().get.underlying()
  }

  implicit def pimpMyDouble(i: Double) = new Implicits.Units(i)
  implicit def pimpMyDecimal(i: BigDecimal) = new Implicits.Units(i)
  implicit def pimpMyInt(i: Int) = new Implicits.Units(i)
}

object Implicits {
  class Units(val i: BigDecimal) extends AnyVal {
    def BTC: Bitcoin.Amount = Bitcoin(i)
    def EUR: Euro.Amount = Euro(i)
    def USD: UsDollar.Amount = UsDollar(i)
  }
}
