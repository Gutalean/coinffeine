package coinffeine.model.payment.okpay

import coinffeine.common.test.UnitTest

class VerificationStatusTest extends UnitTest {

  "A verification status" should "be parsed from its string representation" in {
    VerificationStatus.values.foreach { status =>
      withClue(status) {
        VerificationStatus.parse(status.toString) shouldBe Some(status)
      }
    }
  }

  it should "not be parsed from invalid string" in {
    VerificationStatus.parse("unknown") shouldBe 'empty
  }
}
