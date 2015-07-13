package coinffeine.model.market

import coinffeine.model.order.Price

/** Buyer-seller spread for a given market.
  *
  * Note that prices can be crossed after receiving new orders and before clearing the market.
  *
  * @constructor
  * @param highestBid  Highest price payed by a buyer if any
  * @param lowestAsk   Lowest price asked by a seller if any
  * @tparam C          Currency of the market
  */
case class Spread(highestBid: Option[Price], lowestAsk: Option[Price]) {

  def isCrossed: Boolean = (highestBid, lowestAsk) match {
    case (Some(bid), Some(ask)) => bid.outbidsOrMatches(ask)
    case _ => false
  }

  override def toString = Seq(highestBid, lowestAsk).map(formatPrice).mkString("(", ", ", ")")

  private def formatPrice(priceOpt: Option[Price]) = priceOpt.fold("--") { price =>
    s"${price.value} ${price.currency}"
  }
}

object Spread {
  /** Spread for markets without orders */
  def empty = Spread(None, None)

  def apply(highestBid: Price, lowestAsk: Price): Spread =
    Spread(Some(highestBid), Some(lowestAsk))
}
