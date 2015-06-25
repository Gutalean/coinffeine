package coinffeine.model.currency

import java.math.BigInteger

case object Bitcoin extends Currency {
  override type Amount = BitcoinAmount

  val OneBtcInSatoshi = BigDecimal(100000000)
  override val precision = 8
  override val symbol = "BTC"
  override val preferredSymbolPosition = Currency.SymbolSuffixed
  override val toString = symbol

  def satoshi: Amount = smallestAmount

  override def fromUnits(units: Long) = BitcoinAmount(units)

  def fromSatoshi(amount: BigInteger): BitcoinAmount =
    Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: BigInt): BitcoinAmount = Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: Long): BitcoinAmount = fromSatoshi(BigInt(amount))
}

