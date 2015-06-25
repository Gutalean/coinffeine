package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.FiatAmount
import coinffeine.model.market.{Market, Spread}
import coinffeine.model.order.Price
import coinffeine.protocol.messages.PublicMessage

case class Quote(
    market: Market,
    spread: Spread = Spread.empty,
    lastPrice: Option[Price] = None) extends PublicMessage {

  override def toString = {
    val formattedLastPrice = lastPrice.fold("--")(p => s"${p.value} ${p.currency}")
    s"Quote(spread = $spread, last = $formattedLastPrice)"
  }
}

object Quote {
  def empty(market: Market) = Quote(market)

  /** Utility constructor for the case of having all prices defined */
  def apply(spread: (Price, Price), lastPrice: Price): Quote = Quote(
    market = Market(lastPrice.currency),
    spread = Spread(spread._1, spread._2),
    lastPrice = Some(lastPrice)
  )

  /** Utility constructor for the case of having all prices defined as currency amounts */
  def apply(spread: (FiatAmount, FiatAmount), lastPrice: FiatAmount): Quote =
    Quote(Price(spread._1) -> Price(spread._2), Price(lastPrice))
}
