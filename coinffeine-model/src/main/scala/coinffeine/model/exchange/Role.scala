package coinffeine.model.exchange

import coinffeine.model.Both
import coinffeine.model.market.{Ask, Bid, OrderType}

sealed trait Role {
  def select[A](both: Both[A]): A
  def counterpart: Role
  def buyer[A](mine: A, her: A): A
  def seller[A](mine: A, her: A): A
  def update[A](both: Both[A], value: A): Both[A]
}

object Role {

  def of[A](both: Both[A], elem: A): Option[Role] = elem match {
    case both.`buyer` => Some(BuyerRole)
    case both.`seller` => Some(SellerRole)
    case _ => None
  }

  def fromOrderType(orderType: OrderType): Role = orderType match {
    case Bid => BuyerRole
    case Ask => SellerRole
  }
}

case object BuyerRole extends Role {

  override def select[A](both: Both[A]) = both.buyer

  override def counterpart = SellerRole

  override def toString = "buyer"

  override def buyer[A](mine: A, her: A): A = mine

  override def seller[A](mine: A, her: A): A = her

  override def update[A](both: Both[A], value: A) = both.copy(buyer = value)
}

case object SellerRole extends Role {

  override def select[A](both: Both[A]) = both.seller

  override def counterpart = BuyerRole

  override def toString = "seller"

  override def buyer[A](mine: A, her: A): A = her

  override def seller[A](mine: A, her: A): A = mine

  override def update[A](both: Both[A], value: A) = both.copy(seller = value)
}
