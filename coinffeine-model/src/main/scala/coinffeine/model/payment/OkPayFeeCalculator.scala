package coinffeine.model.payment

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

object OkPayFeeCalculator extends PaymentProcessor {

  val MinFee = BigDecimal(0.01)
  val MaxFee = BigDecimal(2.99)

  def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] = {
    val num = implicitly[Numeric[BigDecimal]]
    val baseFee = amount.value * 0.005
    val fee = num.min(num.max(baseFee, MinFee), MaxFee)
    roundUp(fee, amount.currency)
  }

  private def roundUp[C <: FiatCurrency](amount: BigDecimal, currency: C): CurrencyAmount[C] =
    currency(amount.setScale(currency.precision, RoundingMode.UP))
}
