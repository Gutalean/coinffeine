package coinffeine.model.payment.okpay

import org.joda.time.DateTime

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class TransactionTest extends UnitTest {

  "A transaction" should "have the same currency for net amount and fee" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      Transaction(
        id = 42L,
        senderId = "sender",
        receiverId = "receiver",
        date = DateTime.now(),
        netAmount = 100.EUR,
        fee = 0.05.USD,
        description = "impossible transaction",
        invoice = ""
      )
    }
  }
}
