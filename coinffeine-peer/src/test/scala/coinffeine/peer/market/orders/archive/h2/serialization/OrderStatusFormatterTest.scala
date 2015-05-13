package coinffeine.peer.market.orders.archive.h2.serialization

import coinffeine.common.test.UnitTest
import coinffeine.model.order.OrderStatus

class OrderStatusFormatterTest extends UnitTest {

  "An order status formatter" should "format not stated order status" in {
    OrderStatusFormatter.format(OrderStatus.NotStarted) shouldBe "NotStarted"
  }

  it should "format in progress order status" in {
    OrderStatusFormatter.format(OrderStatus.InProgress) shouldBe "InProgress"
  }

  it should "format completed order status" in {
    OrderStatusFormatter.format(OrderStatus.Completed) shouldBe "Completed"
  }

  it should "format cancelled order status" in {
    OrderStatusFormatter.format(OrderStatus.Cancelled) shouldBe "Cancelled"
  }
}
