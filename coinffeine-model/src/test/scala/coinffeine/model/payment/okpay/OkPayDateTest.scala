package coinffeine.model.payment.okpay

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest

class OkPayDateTest extends UnitTest {

  "OKPay dates" should "be parsed from string" in {
    OkPayDate.unapply("2011-05-16 10:22:33") shouldBe
        Some(DateTime.parse("2011-05-16T10:22:33Z"))
  }

  it should "reject dates in other formats" in {
    OkPayDate.unapply("2011/05/16 10:22") shouldBe 'empty
  }

  it should "be formatted as string" in {
    OkPayDate(DateTime.parse("2011-05-16T10:22:33Z")) shouldBe "2011-05-16 10:22:33"
  }
}
