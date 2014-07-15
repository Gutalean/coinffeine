package coinffeine.model.exchange

/** Utility class for a pair of values belonging to buyer and seller respectively.
  *
  * This class follows the convention for the buyer-seller ordering. Use only with immutable
  * classes.
  */
case class Both[+T](buyer: T, seller: T) {

  /** Access to a value by role */
  def apply(role: Role): T = role match {
    case BuyerRole => buyer
    case SellerRole => seller
  }

  def map[S](f: T => S): Both[S] = Both(
    buyer = f(buyer),
    seller = f(seller)
  )

  def zip[S](other: Both[S]): Both[(T, S)] = Both(
    buyer = (buyer, other.buyer),
    seller = (seller, other.seller)
  )

  def foreach(f: T => Unit): Unit = {
    f(buyer)
    f(seller)
  }

  def forall(pred: T => Boolean): Boolean = pred(buyer) && pred(seller)

  def roleOf[T1 >: T](value: T1): Option[Role] = value match {
    case `buyer` => Some(BuyerRole)
    case `seller` => Some(SellerRole)
    case _ => None
  }

  def updated[T1 >: T](role: Role, value: T1): Both[T1] = role match {
    case BuyerRole => copy(buyer = value)
    case SellerRole => copy(seller = value)
  }

  def toSet[T1 >: T]: Set[T1] = Set(buyer, seller)

  def toSeq: Seq[T] = Seq(buyer, seller)

  def toTuple: (T, T) = (buyer, seller)

  def swap: Both[T] = Both(seller, buyer)
}

object Both {
  def fill[T](value: => T): Both[T] = Both(value, value)

  def fromSeq[T](values: Seq[T]): Both[T] = values match {
    case Seq(buyer, seller) => Both(buyer, seller)
    case _ => throw new IllegalArgumentException(
      s"Cannot build a Both from other than 2 values: ${values.size} given")
  }
}
