package coinffeine.model.order

import coinffeine.common.test.UnitTest

class OrderStatusTest extends UnitTest {

  "Order status" should "parse from string" in {
    OrderStatus.parse("not started") shouldBe Some(OrderStatus.NotStarted)
    OrderStatus.parse("in progress") shouldBe Some(OrderStatus.InProgress)
    OrderStatus.parse("completed") shouldBe Some(OrderStatus.Completed)
    OrderStatus.parse("cancelled") shouldBe Some(OrderStatus.Cancelled)
  }

  it should "fail to parse from invalid string" in {
    OrderStatus.parse("wtf") shouldBe 'empty
  }
}
