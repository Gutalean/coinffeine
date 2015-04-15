package coinffeine.model.market

import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.currency._
import coinffeine.model.network.PeerId

class OrderBookTest extends UnitTest with OptionValues {

  def bid(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.limitBid(btc.BTC, Price(eur, Euro), PositionId(PeerId.hashOf(by), OrderId(orderId)))

  def ask(btc: BigDecimal, eur: BigDecimal, by: String, orderId: String = "1") =
    Position.limitAsk(btc.BTC, Price(eur, Euro), PositionId(PeerId.hashOf(by), OrderId(orderId)))

  def cross(bid: Position[Bid.type, Euro.type],
            ask: Position[Ask.type, Euro.type],
            amount: Bitcoin.Amount) = {
    val averagePrice = bid.price.toOption.get.averageWith(ask.price.toOption.get)
    Cross(
      Both.fill(amount),
      Both.fill(averagePrice.of(amount)),
      Both(buyer = bid.id, seller = ask.id)
    )
  }

  val buyer = PeerId.hashOf("buyer")
  val seller = PeerId.hashOf("seller")
  val user = PeerId.hashOf("user")
  val participants = Both(buyer, seller)
  val emptyBook = OrderBook.empty(Euro)

  "An order book" should "quote a spread" in {
    emptyBook.spread shouldBe Spread.empty
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
    updatedBook.userPositions(PeerId.hashOf("user2")).size should be (2)
  }

  it should "cancel individual orders" in {
    val book = OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      bid(btc = 1, eur = 22, by = "buyer", orderId = "2"),
      ask(btc = 2, eur = 25, by = "seller")
    )
    book.cancelPosition(PositionId(buyer, OrderId("2"))) should be (OrderBook(
      bid(btc = 1, eur = 20, by = "buyer"),
      ask(btc = 2, eur = 25, by = "seller")
    ))
    book.cancelPosition(PositionId(PeerId.hashOf("unknown"), OrderId("1"))) should be (book)
  }

  it should "add new positions when updating user positions as a whole" in {
    val userId = PeerId.hashOf("user")
    val book = emptyBook.updateUserPositions(Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 2.BTC, Price(200.EUR))
    ), userId)
    book.userPositions(userId).toSet shouldBe Set(
      Position.limitBid(1.BTC, Price(100.EUR), PositionId(userId, OrderId("1"))),
      Position.limitBid(2.BTC, Price(200.EUR), PositionId(userId, OrderId("2")))
    )
  }

  it should "remove missing positions when updating user positions as a whole" in {
    val entries = Seq(
      OrderBookEntry(OrderId("1"), Bid, 1.BTC, Price(100.EUR)),
      OrderBookEntry(OrderId("2"), Bid, 1.BTC, Price(200.EUR)),
      OrderBookEntry(OrderId("3"), Bid, 0.5.BTC, Price(230.EUR))
    )
    val positions = Seq(
      Position.limitBid(1.BTC, Price(100.EUR), PositionId(user, OrderId("1"))),
      Position.limitBid(1.BTC, Price(200.EUR), PositionId(user, OrderId("2"))),
      Position.limitBid(0.5.BTC, Price(230.EUR), PositionId(user, OrderId("3")))
    )
    val initialBook = emptyBook.updateUserPositions(entries, user)
    initialBook.userPositions(user).toSet shouldBe positions.toSet
    val finalBook = initialBook.updateUserPositions(entries.tail, user)
    finalBook.userPositions(user).toSet shouldBe positions.tail.toSet
  }

  it should "avoid adding a position twice" in {
    val originalPos = Position.limitBid(1.BTC, Price(100.EUR), PositionId(user, OrderId("1")))
    val book = emptyBook.addPosition(originalPos)
    book.addPosition(originalPos.copy(amount = 0.5.BTC)) should === (book)
  }

  it should "ignore position changes when updating user positions" in {
    val originalPrice = Price(100.EUR)
    val entry = OrderBookEntry(OrderId("1"), Bid, 1.BTC, originalPrice)
    shouldIgnorePositionChange(entry, entry.copy(amount = entry.amount * 2))
    shouldIgnorePositionChange(entry, entry.copy(amount = entry.amount / 2))
    shouldIgnorePositionChange(entry, entry.copy(orderType = Ask))
    shouldIgnorePositionChange(entry, entry.copy(price = LimitPrice(originalPrice.scaleBy(2))))
  }

  private def shouldIgnorePositionChange(originalEntry: OrderBookEntry[Euro.type],
                                         modifiedEntry: OrderBookEntry[Euro.type]): Unit = {
    val originalBook = emptyBook.updateUserPositions(Seq(originalEntry), user)
    originalBook.updateUserPositions(Seq(modifiedEntry), user) shouldBe originalBook
  }

  it should "decrease position amounts on completed handshake" in {
    val bidPosition = bid(btc = 1, eur = 20, by = "buyer")
    val askPosition = ask(btc = 0.5, eur = 20, by = "seller")

    val cross = Cross(Both.fill(0.5.BTC), Both.fill(20.EUR), Both(bidPosition.id, askPosition.id))
    val book = OrderBook(bidPosition, askPosition)
      .startHandshake(cross)
      .completeSuccessfulHandshake(cross)

    book.get(bidPosition.id).value.handshakingAmount shouldBe 0.BTC
    book.get(bidPosition.id).value.amount shouldBe 0.5.BTC
    book.get(askPosition.id) shouldBe 'empty
  }

  it should "drop the positions of the culprits of a failed handshake" in {
    val bidPosition = bid(btc = 1, eur = 20, by = "buyer")
    val askPosition = ask(btc = 1, eur = 20, by = "seller")

    val cross = Cross(Both.fill(1.BTC), Both.fill(20.EUR), Both(bidPosition.id, askPosition.id))
    val book = OrderBook(bidPosition, askPosition)
      .startHandshake(cross)
      .completeFailedHandshake(cross, Set(buyer))

    book.userPositions(buyer) shouldBe 'empty
    book.userPositions(seller).head.amount shouldBe 1.BTC
  }
}
