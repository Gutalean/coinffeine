package coinffeine.model.market

import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.{BitcoinAmount, Euro}
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

  val buyer = PeerId("buyer")
  val seller = PeerId("seller")
  val participants = Both(buyer, seller)

  "An order book" should "quote a spread" in {
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

  it should "mark and clear positions as handshaking" in {
    val bidPosition = bid(btc = 1, eur = 20, by = "buyer")
    val askPosition = ask(btc = 1, eur = 20, by = "seller")
    val cross = Cross(Both.fill(1.BTC), Both.fill(20.EUR), Both(bidPosition.id, askPosition.id))

    var book = OrderBook(bidPosition, askPosition).startHandshake(cross)
    book.get(bidPosition.id).value shouldBe 'inHandshake
    book.get(askPosition.id).value shouldBe 'inHandshake

    book = book.clearHandshake(cross)
    book.get(bidPosition.id).value should not be 'inHandshake
    book.get(askPosition.id).value should not be 'inHandshake
  }
}
