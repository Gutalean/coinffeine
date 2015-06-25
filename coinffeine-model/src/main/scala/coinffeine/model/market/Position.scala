package coinffeine.model.market

import coinffeine.model.currency.{FiatCurrency, Bitcoin, BitcoinAmount}
import coinffeine.model.order._

/** Bidding or asking position taken by a requester */
case class Position[T <: OrderType](
    orderType: T,
    amount: BitcoinAmount,
    price: OrderPrice,
    id: PositionId,
    handshakingAmount: BitcoinAmount = Bitcoin.zero) {
  require(amount.isPositive && !handshakingAmount.isNegative && amount >= handshakingAmount,
    s"Invalid position amounts: $this")

  /** Amount not involved in handshaking and thus available for crossing */
  def availableAmount: BitcoinAmount = amount - handshakingAmount

  def decreaseAmount(decrease: BitcoinAmount): Position[T] = {
    require(decrease < amount)
    copy(amount = amount - decrease)
  }

  def clearHandshake(crossedAmount: BitcoinAmount): Position[T] =
    copy(handshakingAmount = (handshakingAmount - crossedAmount) max Bitcoin.zero)

  def startHandshake(crossedAmount: BitcoinAmount): Position[T] =
    copy(handshakingAmount = handshakingAmount + crossedAmount)

  /** Folds any Position type into a value of type T.
    *
    * @param bid       Transformation for bid positions
    * @param ask       Transformation for ask positions
    * @tparam R        Return type
    * @return          Transformed input
    */
  def fold[R](bid: BidPosition => R, ask: AskPosition => R): R =
    orderType match {
      case _: Bid.type => bid(this.asInstanceOf[BidPosition])
      case _: Ask.type => ask(this.asInstanceOf[AskPosition])
    }

  def toOrderBookEntry: OrderBookEntry = OrderBookEntry(id.orderId, orderType, amount, price)
}

object Position {

  def marketBid(
      amount: BitcoinAmount,
      currency: FiatCurrency,
      requester: PositionId,
      handshakingAmount: BitcoinAmount = Bitcoin.zero): BidPosition =
    Position(Bid, amount, MarketPrice(currency), requester, handshakingAmount)

  def limitBid(
      amount: BitcoinAmount,
      price: Price,
      requester: PositionId,
      handshakingAmount: BitcoinAmount = Bitcoin.zero): BidPosition =
    Position(Bid, amount, LimitPrice(price), requester, handshakingAmount)

  def marketAsk(
      amount: BitcoinAmount,
      currency: FiatCurrency,
      requester: PositionId,
      handshakingAmount: BitcoinAmount = Bitcoin.zero): AskPosition =
    Position(Ask, amount, MarketPrice(currency), requester, handshakingAmount)

  def limitAsk(
      amount: BitcoinAmount,
      price: Price,
      requester: PositionId,
      handshakingAmount: BitcoinAmount = Bitcoin.zero): AskPosition =
    Position(Ask, amount, LimitPrice(price), requester, handshakingAmount)
}

