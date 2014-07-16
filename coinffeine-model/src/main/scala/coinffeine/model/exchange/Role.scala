package coinffeine.model.exchange

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}

sealed trait Role {
  def apply[A](both: Both[A]): A
  def counterpart: Role
  def myDepositAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount
  def myRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount
  def herRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount
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
}

object BuyerRole extends Role {

  override def apply[A](both: Both[A]) = both.buyer

  override def counterpart = SellerRole

  override def toString = "buyer"

  override def myDepositAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.buyerDeposit

  override def myRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.buyerRefund

  override def herRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.sellerRefund

  override def buyer[A](mine: A, her: A): A = mine

  override def seller[A](mine: A, her: A): A = her

  override def update[A](both: Both[A], value: A) = both.copy(buyer = value)
}

object SellerRole extends Role {

  override def apply[A](both: Both[A]) = both.seller

  override def counterpart = BuyerRole

  override def toString = "seller"

  override def myDepositAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.sellerDeposit

  override def myRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.sellerRefund

  override def herRefundAmount(amounts: Exchange.Amounts[_ <: FiatCurrency]): BitcoinAmount =
    amounts.buyerRefund

  override def buyer[A](mine: A, her: A): A = her

  override def seller[A](mine: A, her: A): A = mine

  override def update[A](both: Both[A], value: A) = both.copy(seller = value)
}
