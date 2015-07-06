package coinffeine.model.payment.okpay

import coinffeine.common.test.UnitTest

class PageTest extends UnitTest {

  "A page" should "not be constructed from invalid page sizes" in new {
    an [IllegalArgumentException] shouldBe thrownBy { Page(size = -1, number = 1) }
    an [IllegalArgumentException] shouldBe thrownBy { Page(size = 0, number = 1) }
    an [IllegalArgumentException] shouldBe thrownBy { Page(size = Page.MaxSize + 1, number = 1) }
  }

  it should "not be constructed from invalid page numbers" in {
    an [IllegalArgumentException] shouldBe thrownBy { Page(size = 10, number = -1) }
    an [IllegalArgumentException] shouldBe thrownBy { Page(size = 10, number = 0) }
  }

  it should "step to the next page" in {
    Page(size = 2, number = 1).next shouldBe Page(size = 2, number = 2)
  }

  it should "compute the range of elements it represents" in {
    val firstPage = Page(size = 10, number = 1)
    firstPage.rangeStart shouldBe 0
    firstPage.rangeEnd shouldBe firstPage.size

    val secondPage = firstPage.next
    secondPage.rangeStart shouldBe firstPage.rangeEnd
    secondPage.rangeEnd shouldBe (firstPage.rangeEnd + firstPage.size)
  }
}
