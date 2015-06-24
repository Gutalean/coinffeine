package coinffeine.model.currency2

import scala.language.implicitConversions

import org.bitcoinj.core.Coin

trait Implicits {
  import Implicits.Units

  implicit def pimpMyDouble(i: Double): Units = new Implicits.Units(i)
  implicit def pimpMyDecimal(i: BigDecimal): Units = new Implicits.Units(i)
  implicit def pimpMyInt(i: Int): Units = new Implicits.Units(i)

  implicit def convertToBitcoinjCoin(amount: Bitcoin.Amount): Coin = Coin.valueOf(amount.units)
  implicit def convertToBitcoinAmount(amount: Coin): Bitcoin.Amount = Bitcoin(amount.value)
}

object Implicits {
  class Units(val i: BigDecimal) extends AnyVal {
    def BTC: BitcoinAmount = Bitcoin(i)
    def EUR: FiatAmount = Euro(i)
    def USD: FiatAmount = UsDollar(i)
  }
}
