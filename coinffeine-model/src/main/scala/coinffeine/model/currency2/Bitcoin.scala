package coinffeine.model.currency2

import java.math.BigInteger

case object Bitcoin extends Currency {
  override type Amount = BitcoinAmount

  val OneBtcInSatoshi = BigDecimal(100000000)
  override val precision = 8
  override val symbol = "BTC"
  override val preferredSymbolPosition = Currency.SymbolSuffixed
  override val toString = symbol

  override def fromUnits(units: Long) = BitcoinAmount(units)

  def fromSatoshi(amount: BigInteger): Bitcoin.Amount =
    Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: BigInt): Bitcoin.Amount = Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: Long): Bitcoin.Amount = fromSatoshi(BigInt(amount))
}

