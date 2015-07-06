package coinffeine.model.payment.okpay

case class TransactionHistoryPage(page: Page, totalSize: Int, transactions: Seq[Transaction]) {
  require(transactions.size <= page.size, s"Cannot hold more transactions than the page size")
  require(totalSize >= transactions.size,
    s"Total size should be at least the size of the transactions in this page: $this")

  def totalPages: Int = completePages + incompletePages

  private def completePages = totalSize / page.size

  private def incompletePages = if (totalSize % page.size > 0) 1 else 0
}
