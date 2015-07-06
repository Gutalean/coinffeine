package coinffeine.model.payment.okpay

import coinffeine.common.test.UnitTest

class TransactionHistoryPageTest extends UnitTest {

  val page = Page(size = 2, number = 1)

  "A transaction history page" should "not have more transactions than the page size" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      TransactionHistoryPage(page, totalSize = 3, transactions = Seq(null, null, null))
    }
  }

  it should "not have more transactions than the total size" in {
    an [IllegalArgumentException] shouldBe thrownBy {
      TransactionHistoryPage(page, totalSize = 1, transactions = Seq(null, null))
    }
  }

  it should "compute the total page count" in {
    TransactionHistoryPage(page, totalSize = 0, transactions = Seq.empty).totalPages shouldBe 0
    TransactionHistoryPage(page, totalSize = 1, transactions = Seq(null)).totalPages shouldBe 1
    TransactionHistoryPage(page, totalSize = 2, transactions = Seq(null, null)).totalPages shouldBe 1
    TransactionHistoryPage(page, totalSize = 3, transactions = Seq(null, null)).totalPages shouldBe 2
  }
}
