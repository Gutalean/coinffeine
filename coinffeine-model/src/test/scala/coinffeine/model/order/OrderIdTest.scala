package coinffeine.model.order

import coinffeine.common.test.UnitTest

class OrderIdTest extends UnitTest {

  "An order id" should "be parsed from an UUID" in {
    val id = OrderId.random()
    OrderId.parse(id.value) shouldBe Some(id)
  }

  it should "not be parsed from invalid strings" in {
    OrderId.parse("invalid") shouldBe 'empty
  }
}
