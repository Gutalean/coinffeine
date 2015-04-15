package coinffeine.model.currency

import java.math.BigInteger

case object Bitcoin extends Currency {
  val OneBtcInSatoshi = BigDecimal(100000000)
  override val precision = 8
  override val symbol = "BTC"
  override val preferredSymbolPosition = Currency.SymbolSuffixed
  override val toString = symbol

  def fromSatoshi(amount: BigInteger): Bitcoin.Amount =
    Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: BigInt): Bitcoin.Amount = Bitcoin(BigDecimal(amount) / OneBtcInSatoshi)
  def fromSatoshi(amount: Long): Bitcoin.Amount = fromSatoshi(BigInt(amount))
}

