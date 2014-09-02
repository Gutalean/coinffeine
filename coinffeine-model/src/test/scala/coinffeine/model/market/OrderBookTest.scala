package coinffeine.model.market

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.{FiatCurrency, BitcoinAmount}
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.{ExchangeId, Both}
import coinffeine.model.network.PeerId

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

  def clearAllCrosses[C <: FiatCurrency](book: OrderBook[C]): OrderBook[C] = {
    var intermediateBook = book
    for ((cross, index) <- book.crosses.zipWithIndex) {
      val exchangeId = ExchangeId(index.toString)
      intermediateBook = intermediateBook.startHandshake(exchangeId, cross)
        .completeHandshake(exchangeId)
    }
    intermediateBook
  }


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
    updatedBook.userPositions(PeerId("user2")).size should be (2)
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

  it should "not consider positions for matching once they are handshaking" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer1"),
      bid(btc = 1, eur = 20, by = "buyer2"),
      ask(btc = 2, eur = 20, by = "seller")
    )
    val cross = book.crosses.head
    book.startHandshake(ExchangeId.random(), cross) should not be 'crossed
  }

  it should "be cleared with no changes when there is no cross" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    )
    clearAllCrosses(book) should be (book)
  }

  it should "be cleared with a cross when two orders match perfectly" in {
    val crossedBid = bid(btc = 2, eur = 25, by = "buyer")
    val crossedAsk = ask(btc = 2, eur = 25, by = "seller")

    val book = OrderBook(crossedBid, bid(btc = 1, eur = 20, by = "other buyer"), crossedAsk)

    book.crosses should be (Seq(cross(crossedBid, crossedAsk, 2.BTC)))
    clearAllCrosses(book) should be(OrderBook(
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
    clearAllCrosses(book) should be (clearedBook)
  }

  it should "cancel handshakes" in {
    var book = OrderBook(
      bid(btc = 1, eur = 100, by = "buyer"),
      ask(btc = 1, eur = 100, by = "seller")
    )
    val originalCross = book.crosses.head

    val exchangeId = ExchangeId("cancellable")
    book = book.startHandshake(exchangeId, originalCross)
    book should not be 'crossed

    book.cancelHandshake(exchangeId).crosses should be (Seq(originalCross))
  }

  it should "clear multiple orders against one if necessary" in {
    val buyerOrder = bid(btc = 5, eur = 25, by = "buyer")
    val sellerOrder1 = ask(btc = 2, eur = 15, by = "seller1")
    val sellerOrder2 = ask(btc = 2, eur = 20, by = "seller2")
    val sellerOrder3 = ask(btc = 2, eur = 25, by = "seller3")

    var book = OrderBook(buyerOrder, sellerOrder1, sellerOrder2, sellerOrder3)
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder1, 2.BTC)))

    book = book.startHandshake(ExchangeId("1"), book.crosses.head)
      .completeHandshake(ExchangeId("1"))
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder2, 2.BTC)))

    book = book.startHandshake(ExchangeId("2"), book.crosses.head)
      .completeHandshake(ExchangeId("2"))
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder3, 1.BTC)))

    book = book.startHandshake(ExchangeId("3"), book.crosses.head)
      .completeHandshake(ExchangeId("3"))
    book should be(OrderBook(ask(btc = 1, eur = 25, by = "seller3")))
  }
}
