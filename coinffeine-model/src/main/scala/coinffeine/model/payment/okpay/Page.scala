package coinffeine.model.payment.okpay

case class Page(size: Int, number: Int) {
  require(size > 0 && size <= Page.MaxSize, s"Invalid page size: $size")
  require(number > 0, s"Invalid page number: $number")

  /** Inclusive start of the range of elements */
  def rangeStart = size * (number - 1)

  /** Exclusive end of the range of elements */
  def rangeEnd = size * number

  def next: Page = copy(number = number + 1)
}

object Page {
  val MaxSize = 50

  def first(size: Int) = Page(size, number = 1)
}
