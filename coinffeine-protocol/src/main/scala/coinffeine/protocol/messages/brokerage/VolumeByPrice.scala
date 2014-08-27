package coinffeine.protocol.messages.brokerage

import coinffeine.model.currency.Implicits._
import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}

case class VolumeByPrice[C <: FiatCurrency](entries: Seq[(CurrencyAmount[C], BitcoinAmount)]) {

  val prices = entries.map(_._1)
  require(prices.toSet.size == entries.size, s"Repeated prices: ${prices.mkString(",")}")

  def entryMap = entries.toMap[CurrencyAmount[C], BitcoinAmount]

  requirePositiveValues()

  def highestPrice: Option[CurrencyAmount[C]] = prices.reduceOption(_ max _)
  def lowestPrice: Option[CurrencyAmount[C]] = prices.reduceOption(_ min _)

  def isEmpty = entries.isEmpty

  /** Volume at a given price */
  def volumeAt(price: CurrencyAmount[C]): BitcoinAmount = entryMap.getOrElse(price, 0.BTC)

  def increase(price: CurrencyAmount[C], amount: BitcoinAmount): VolumeByPrice[C] =
    copy(entries = entryMap.updated(price, volumeAt(price) + amount).toSeq)

  def decrease(price: CurrencyAmount[C], amount: BitcoinAmount): VolumeByPrice[C] = {
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
  def apply[C <: FiatCurrency](
      pair: (CurrencyAmount[C], BitcoinAmount),
      otherPairs: (CurrencyAmount[C], BitcoinAmount)*): VolumeByPrice[C] = {
    val pairs = pair +: otherPairs
    VolumeByPrice(pairs)
  }

  def empty[C <: FiatCurrency]: VolumeByPrice[C] =
    VolumeByPrice(Seq.empty[(CurrencyAmount[C], BitcoinAmount)])
}
