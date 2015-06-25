package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency._

case class VolumeByPrice(entries: Seq[(FiatAmount, BitcoinAmount)]) {

  val prices = entries.map(_._1)
  require(prices.toSet.size == entries.size, s"Repeated prices: ${prices.mkString(",")}")

  def entryMap = entries.toMap[FiatAmount, BitcoinAmount]

  requirePositiveValues()

  def highestPrice: Option[FiatAmount] = prices.reduceOption(_ max _)
  def lowestPrice: Option[FiatAmount] = prices.reduceOption(_ min _)

  def isEmpty = entries.isEmpty

  /** Volume at a given price */
  def volumeAt(price: FiatAmount): BitcoinAmount = entryMap.getOrElse(price, 0.BTC)

  def increase(price: FiatAmount, amount: BitcoinAmount): VolumeByPrice =
    copy(entries = entryMap.updated(price, volumeAt(price) + amount).toSeq)

  def decrease(price: FiatAmount, amount: BitcoinAmount): VolumeByPrice = {
    val previousAmount = volumeAt(price)
    if (previousAmount > amount) copy(entries = entryMap.updated(price, previousAmount - amount).toSeq)
    else copy(entries = (entryMap - price).toSeq)
  }

  private def requirePositiveValues(): Unit = {
    entries.foreach { case (price, amount) =>
        require(amount.isPositive, "Amount ordered must be strictly positive")
        require(price.isPositive, "Price must be strictly positive")
    }
  }
}

object VolumeByPrice {

  /** Convenience factory method */
  def apply(
      pair: (FiatAmount, BitcoinAmount),
      otherPairs: (FiatAmount, BitcoinAmount)*): VolumeByPrice = {
    val pairs = pair +: otherPairs
    VolumeByPrice(pairs)
  }

  def empty: VolumeByPrice =
    VolumeByPrice(Seq.empty[(FiatAmount, BitcoinAmount)])
}
