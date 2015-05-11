package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}
import coinffeine.model.market.{Market, Spread}
import coinffeine.model.order.Price
import coinffeine.protocol.messages.PublicMessage

case class Quote[C <: FiatCurrency](
    market: Market[C],
    spread: Spread[C] = Spread.empty[C],
    lastPrice: Option[Price[C]] = None) extends PublicMessage {

  override def toString = {
    val formattedLastPrice = lastPrice.fold("--")(p => s"${p.value} ${p.currency}")
    s"Quote(spread = $spread, last = $formattedLastPrice)"
  }
}

object Quote {
  def empty[C <: FiatCurrency](market: Market[C]) = Quote[C](market)

  /** Utility constructor for the case of having all prices defined */
  def apply[C <: FiatCurrency](spread: (Price[C], Price[C]), lastPrice: Price[C]): Quote[C] = Quote(
    market = Market(lastPrice.currency),
    spread = Spread(spread._1, spread._2),
    lastPrice = Some(lastPrice)
  )

  /** Utility constructor for the case of having all prices defined as currency amounts */
  def apply[C <: FiatCurrency](spread: (CurrencyAmount[C], CurrencyAmount[C]),
                               lastPrice: CurrencyAmount[C]): Quote[C] =
    Quote(Price(spread._1) -> Price(spread._2), Price(lastPrice))
}
