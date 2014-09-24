package coinffeine.model.market

import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.{FiatCurrency, BitcoinAmount}
import coinffeine.model.currency.Currency.Euro
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.Both
import coinffeine.model.network.PeerId

class OrderBookTest extends UnitTest with OptionValues {
  def bid(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.bid(btc.BTC, Price(eur, Euro), PositionId(PeerId(by), OrderId(orderId)))

  def ask(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.ask(btc.BTC, Price(eur, Euro), PositionId(PeerId(by), OrderId(orderId)))

  def cross(bid: Position[Bid.type, Euro.type],
            ask: Position[Ask.type, Euro.type],
            amount: BitcoinAmount) = {
    val averagePrice = bid.price.averageWith(ask.price)
    Cross(
      Both.fill(amount),
      Both.fill(averagePrice.of(amount)),
      Both(buyer = bid.id, seller = ask.id)
    )
  }

  def clearAllCrosses[C <: FiatCurrency](book: OrderBook[C]): OrderBook[C] =
    book.crosses.foldLeft(book) { (book, cross) =>
      book.startHandshake(cross).completeHandshake(cross)
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
    OrderBook.empty(Euro).spread shouldBe Spread.empty
    OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    ).spread shouldBe Spread(Price(20 EUR), Price(25 EUR))
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

  it should "add new positions when updating user positions as a whole" in {
    val userId = PeerId("user")
    val emptyBook = OrderBook.empty(Euro)
    val book = emptyBook.updateUserPositions(Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 2.BTC, Price(200.EUR))
    ), userId)
    book.userPositions(userId).toSet shouldBe Set(
      Position(Bid, 1.BTC, Price(100.EUR), PositionId(userId, OrderId("1"))),
      Position(Bid, 2.BTC, Price(200.EUR), PositionId(userId, OrderId("2")))
    )
  }

  it should "remove missing positions when updating user positions as a whole" in {
    val userId = PeerId("user")
    val entries = Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 1.BTC, Price(200.EUR)),
      OrderBookEntry(OrderId("3"), Bid, 0.5.BTC, Price(230.EUR))
    )
    val positions = Seq(
      Position(Bid, 1.BTC, Price(100.EUR), PositionId(userId, OrderId("1"))),
      Position(Bid, 1.BTC, Price(200.EUR), PositionId(userId, OrderId("2"))),
      Position(Bid, 0.5.BTC, Price(230.EUR), PositionId(userId, OrderId("3")))
    )
    val initialBook = OrderBook.empty(Euro).updateUserPositions(entries, userId)
    initialBook.userPositions(userId).toSet shouldBe positions.toSet
    val finalBook = initialBook.updateUserPositions(entries.tail, userId)
    finalBook.userPositions(userId).toSet shouldBe positions.tail.toSet
  }

  it should "update modified position amounts when updating user positions as a whole" in {
    val userId = PeerId("user")
    val originalEntries = Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 1.BTC, Price(200.EUR))
    )
    val modifiedEntries = Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 0.5.BTC, Price(200.EUR))
    )
    OrderBook.empty(Euro)
      .updateUserPositions(originalEntries, userId)
      .updateUserPositions(modifiedEntries, userId)
      .get(PositionId(userId, OrderId("2"))).value.amount shouldBe 0.5.BTC
  }

  it should "ignore position changes other than decreasing the amount when updating user positions" in {
    val entry = OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR))
    shouldIgnorePositionChange(entry, entry.copy(amount = entry.amount * 2))
    shouldIgnorePositionChange(entry, entry.copy(orderType = Ask))
    shouldIgnorePositionChange(entry, entry.copy(price = entry.price.scaleBy(2)))
  }

  private def shouldIgnorePositionChange(originalEntry: OrderBookEntry[Euro.type],
                                         modifiedEntry: OrderBookEntry[Euro.type]): Unit = {
    val userId = PeerId("user")
    val originalBook = OrderBook.empty(Euro).updateUserPositions(Seq(originalEntry), userId)
    originalBook.updateUserPositions(Seq(modifiedEntry), userId) shouldBe originalBook
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
    book.startHandshake(book.crosses.head) should not be 'crossed
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
    book.crosses.map(_.bitcoinAmounts) should be (Seq(Both.fill(1.BTC)))
    val clearedBook = OrderBook(bid(btc = 1, eur = 25, by = "buyer"))
    clearAllCrosses(book) should be (clearedBook)
  }

  it should "cancel handshakes" in {
    var book = OrderBook(
      bid(btc = 1, eur = 100, by = "buyer"),
      ask(btc = 1, eur = 100, by = "seller")
    )
    val originalCross = book.crosses.head

    book = book.startHandshake(originalCross)
    book should not be 'crossed

    book.clearHandshake(originalCross).crosses should be (Seq(originalCross))
  }

  it should "clear multiple orders against one if necessary" in {
    val buyerOrder = bid(btc = 5, eur = 25, by = "buyer")
    val sellerOrder1 = ask(btc = 2, eur = 15, by = "seller1")
    val sellerOrder2 = ask(btc = 2, eur = 20, by = "seller2")
    val sellerOrder3 = ask(btc = 2, eur = 25, by = "seller3")

    var book = OrderBook(buyerOrder, sellerOrder1, sellerOrder2, sellerOrder3)
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder1, 2.BTC)))

    val cross1 = book.crosses.head
    book = book.startHandshake(cross1).completeHandshake(cross1)
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder2, 2.BTC)))

    val cross2 = book.crosses.head
    book = book.startHandshake(cross2).completeHandshake(cross2)
    book.crosses should be (Seq(cross(buyerOrder, sellerOrder3, 1.BTC)))

    val cross3 = book.crosses.head
    book = book.startHandshake(cross3).completeHandshake(cross3)
    book should be(OrderBook(ask(btc = 1, eur = 25, by = "seller3")))
  }
}
