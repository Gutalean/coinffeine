package coinffeine.model.currency.balance

import coinffeine.model.currency._
import coinffeine.model.util.CacheStatus

trait Balance[A <: CurrencyAmount[A]] {
  def amount: A

  def status: CacheStatus
}

case class BitcoinBalance(
    estimated: BitcoinAmount,
    available: BitcoinAmount,
    minOutput: Option[BitcoinAmount],
    blocked: BitcoinAmount = Bitcoin.zero,
    status: CacheStatus = CacheStatus.Fresh) extends Balance[BitcoinAmount] {

  val amount = estimated
}

object BitcoinBalance {

  def singleOutput(amount: BitcoinAmount) = BitcoinBalance(
    estimated = amount,
    available = amount,
    minOutput = Some(amount))
}

case class FiatBalance(
    amount: FiatAmount,
    status: CacheStatus = CacheStatus.Fresh) extends Balance[FiatAmount]

