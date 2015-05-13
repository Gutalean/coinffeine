package coinffeine.model.order

import coinffeine.common.test.UnitTest

class OrderStatusTest extends UnitTest {

  "Order status" should "parse from string" in {
    OrderStatus.parse("not started") shouldBe Some(NotStartedOrder)
    OrderStatus.parse("in progress") shouldBe Some(InProgressOrder)
    OrderStatus.parse("completed") shouldBe Some(CompletedOrder)
    OrderStatus.parse("cancelled") shouldBe Some(CancelledOrder)
  }

  it should "fail to parse from invalid string" in {
    OrderStatus.parse("wtf") shouldBe 'empty
  }
}
