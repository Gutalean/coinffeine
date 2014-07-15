package coinffeine.model.market

import coinffeine.model.currency.BitcoinAmount
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Both
import coinffeine.model.network.PeerId
import com.coinffeine.common.test.UnitTest

class OrderBookTest extends UnitTest {
  def bid(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.bid(btc.BTC, eur.EUR, PositionId(PeerId(by), OrderId(orderId)))

  def ask(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.ask(btc.BTC, eur.EUR, PositionId(PeerId(by), OrderId(orderId)))

  def cross(bid: Position[Bid.type, Euro.type], ask: Position[Ask.type, Euro.type],
            amount: BitcoinAmount) =
    OrderBook.Cross(amount, (bid.price + ask.price) / 2, Both(
      buyer = bid.id,
      seller = ask.id
    ))

  val buyer = PeerId("buyer")
  val seller = PeerId("seller")
  val participants = Both(buyer, seller)

  "An order book" should "detect a cross when a bid price is greater than an ask one" in {
    OrderBook.empty(Euro) should not be 'crossed
    OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    ) should not be 'crossed
    OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 15, by = "seller")
    ) should be ('crossed)
  }

  it should "quote a spread" in {
    OrderBook.empty(Euro).spread should be ((None, None))
    OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    ).spread should be (Some(20 EUR), Some(25 EUR))
  }

  it should "keep previous unresolved orders when inserting a new one" in {
    val book = OrderBook(
      bid(btc = 4, eur = 120, by = "user1"),
      bid(btc = 3, eur = 95, by = "user2"),
      ask(btc = 3, eur = 125, by = "user3")
    )
    val updatedBook = book.addPosition(bid(btc = 3, eur = 120, by = "user2", orderId = "2"))
    updatedBook.positions.count(p => p.id.peerId == PeerId("user2")) should be (2)
  }

  it should "cancel positions by requester" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      bid(btc = 1, eur = 22, by = "buyer", orderId = "2"),
      ask(btc = 2, eur = 25, by = "seller")
    )
    book.cancelAllPositions(buyer) should be (OrderBook(
      ask(btc = 2, eur = 25, by = "seller")
    ))
    book.cancelAllPositions(PeerId("unknown")) should be (book)
  }

  it should "cancel individual orders" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      bid(btc = 1, eur = 22, by = "buyer", orderId = "2"),
      ask(btc = 2, eur = 25, by = "seller")
    )
    book.cancelPosition(PositionId(PeerId("buyer"), OrderId("2"))) should be (OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    ))
    book.cancelPosition(PositionId(PeerId("unknown"), OrderId("1"))) should be (book)
  }

  it should "decrease the amount of a position" in {
    val unchangedOrder = ask(btc = 2, eur = 25, by = "seller")
    val book = OrderBook(bid(btc = 1, eur = 20, by = "buyer"), unchangedOrder)
    book.decreaseAmount(PositionId(PeerId("buyer"), OrderId("1")), 0.8.BTC) should be (
      OrderBook(bid(btc = 0.2, eur = 20, by = "buyer"), unchangedOrder))
  }

  it should "decrease the amount of a position to cancel it completely" in {
    val unchangedOrder = ask(btc = 2, eur = 25, by = "seller")
    val book = OrderBook(bid(btc = 1, eur = 20, by = "buyer"), unchangedOrder)
    book.decreaseAmount(PositionId(PeerId("buyer"), OrderId("1")), 1.BTC) should be (
      OrderBook(unchangedOrder))
  }

  it should "be cleared with no changes when there is no cross" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    )
    book.clearMarket should be (book)
  }

  it should "be cleared with a cross when two orders match perfectly" in {
    val crossedBid = bid(btc = 2, eur = 25, by = "buyer")
    val crossedAsk = ask(btc = 2, eur = 25, by = "seller")

    val book = OrderBook(crossedBid, bid(btc = 1, eur = 20, by = "other buyer"), crossedAsk)

    book.crosses should be (Seq(cross(crossedBid, crossedAsk, 2.BTC)))
    book.clearMarket should be(OrderBook(
      bid(btc = 1, eur = 20, by = "other buyer")
    ))
  }

  it should "use the midpoint price" in {
    val buyerOrder = bid(btc = 1, eur = 20, by = "buyer")
    val sellerOrder = ask(btc = 1, eur = 10, by = "seller")
    val book = OrderBook(buyerOrder, sellerOrder)
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder, 1.BTC)))
  }

  it should "clear orders partially" in {
    val book = OrderBook(
      bid(btc = 2, eur = 25, by = "buyer"),
      ask(btc = 1, eur = 25, by = "seller")
    )
    book.crosses.map(_.amount) should be (Seq(1.BTC))
    val clearedBook = OrderBook(bid(btc = 1, eur = 25, by = "buyer"))
    book.clearMarket should be (clearedBook)
  }

  it should "clear multiple orders against one if necessary" in {
    val buyerOrder = bid(btc = 5, eur = 25, by = "buyer")
    val sellerOrder1 = ask(btc = 2, eur = 15, by = "seller1")
    val sellerOrder2 = ask(btc = 2, eur = 20, by = "seller2")
    val sellerOrder3 = ask(btc = 2, eur = 25, by = "seller3")
    val book = OrderBook(buyerOrder, sellerOrder1, sellerOrder2, sellerOrder3)
    book.crosses should be (Seq(
      cross(buyerOrder, sellerOrder1, 2.BTC),
      cross(buyerOrder, sellerOrder2, 2.BTC),
      cross(buyerOrder, sellerOrder3, 1.BTC)
    ))
    book.clearMarket should be(OrderBook(ask(btc = 1, eur = 25, by = "seller3")))
  }
}
