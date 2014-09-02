package coinffeine.model.market

import coinffeine.model.currency.{BitcoinAmount, FiatCurrency}
import coinffeine.model.exchange.ExchangeId

/** Bidding or asking position taken by a requester */
case class Position[T <: OrderType, C <: FiatCurrency](orderType: T,
                                                       amount: BitcoinAmount,
                                                       price: Price[C],
                                                       id: PositionId,
                                                       handshake: Option[ExchangeId] = None) {
  val inHandshake: Boolean = handshake.isDefined

  def decreaseAmount(decrease: BitcoinAmount): Position[T, C] = {
    require(decrease < amount)
    copy(amount = amount - decrease)
  }

  /** Folds any Position type into a value of type T.
    *
    * @param bid       Transformation for bid positions
    * @param ask       Transformation for ask positions
    * @tparam R        Return type
    * @return          Transformed input
    */
  def fold[R](bid: Position[Bid.type, C] => R, ask: Position[Ask.type, C] => R): R =
    orderType match {
      case _: Bid.type => bid(this.asInstanceOf[Position[Bid.type, C]])
      case _: Ask.type => ask(this.asInstanceOf[Position[Ask.type, C]])
    }

  def toOrderBookEntry: OrderBookEntry[C] = OrderBookEntry(id.orderId, orderType, amount, price)
}

object Position {

  def bid[C <: FiatCurrency](
      amount: BitcoinAmount,
      price: Price[C],
      requester: PositionId): Position[Bid.type, C] =
    Position(Bid, amount, price, requester)

  def ask[C <: FiatCurrency](
      amount: BitcoinAmount,
      price: Price[C],
      requester: PositionId): Position[Ask.type, C] =
    Position(Ask, amount, price, requester)
}

