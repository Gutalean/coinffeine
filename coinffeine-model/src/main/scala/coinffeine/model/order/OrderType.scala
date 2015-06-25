package coinffeine.model.order

sealed trait OrderType {
  def name: String
  def shortName: String
  def priceOrdering: Ordering[OrderPrice]
  def oppositeType: OrderType

  override def toString = name
}

object OrderType {
  val values = Seq(Bid, Ask)

  def parse(str: String): Option[OrderType] = values.find(_.shortName == str)
}

/** Trying to buy bitcoins */
case object Bid extends OrderType {
  override val name = "Bid (buy)"
  override val shortName = "bid"
  override def priceOrdering = Ordering.fromLessThan[OrderPrice] {
    case (MarketPrice(_), _) => true
    case (limitPrice, MarketPrice(_)) => false
    case (LimitPrice(left), LimitPrice(right)) => left.outbids(right)
    case _ => true
  }
  override def oppositeType = Ask
}

/** Trying to sell bitcoins */
case object Ask extends OrderType {
  override val name = "Ask (sell)"
  override val shortName = "ask"
  override def priceOrdering = Ordering.fromLessThan[OrderPrice] {
    case (MarketPrice(_), _) => true
    case (limitPrice, MarketPrice(_)) => false
    case (LimitPrice(left), LimitPrice(right)) => left.underbids(right)
    case _ => true
  }
  override def oppositeType = Bid
}
