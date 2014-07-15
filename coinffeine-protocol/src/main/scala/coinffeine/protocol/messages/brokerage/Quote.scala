package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.market.OrderBook
import coinffeine.protocol.messages.PublicMessage

case class Quote[+C <: FiatCurrency](
    market: Market[C],
    spread: OrderBook.Spread[C] = (None, None),
    lastPrice: Option[CurrencyAmount[C]] = None) extends PublicMessage {
  override def toString = "Quote(spread = (%s, %s), last = %s)".format(
    spread._1.getOrElse("--"),
    spread._2.getOrElse("--"),
    lastPrice.getOrElse("--")
  )
}

object Quote {
  def empty[C <: FiatCurrency](market: Market[C]): Quote[C] = Quote(market)

  /** Utility constructor for the case of having all prices defined */
  def apply[C <: FiatCurrency](
      spread: (CurrencyAmount[C], CurrencyAmount[C]), lastPrice: CurrencyAmount[C]): Quote[C] =
    Quote(
      market = Market(lastPrice.currency),
      spread = Some(spread._1) -> Some(spread._2),
      lastPrice = Some(lastPrice)
    )
}
