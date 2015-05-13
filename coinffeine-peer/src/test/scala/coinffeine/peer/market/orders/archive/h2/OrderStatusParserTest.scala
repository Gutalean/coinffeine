package coinffeine.peer.market.orders.archive.h2

import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest
import coinffeine.model.order.OrderStatus

class OrderStatusParserTest extends UnitTest {

  "An order status parser" should "parse not started order" in {
    OrderStatusParser.parse("NotStarted") shouldBe OrderStatus.NotStarted.some
  }

  it should "parse in progress order" in {
    OrderStatusParser.parse("InProgress") shouldBe OrderStatus.InProgress.some
  }

  it should "parse completed order" in {
    OrderStatusParser.parse("Completed") shouldBe OrderStatus.Completed.some
  }

  it should "parse cancelled order" in {
    OrderStatusParser.parse("Cancelled") shouldBe OrderStatus.Cancelled.some
  }

  it should "failed to parse invalid string" in {
    OrderStatusParser.parse("No. I AM YOUR FATHER!!!") shouldBe None
  }
}
