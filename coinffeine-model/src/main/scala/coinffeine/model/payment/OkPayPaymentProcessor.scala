package coinffeine.model.payment

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.Currency.{UsDollar, Euro}
import coinffeine.model.currency.{CurrencyAmount, FiatCurrency}

object OkPayPaymentProcessor extends PaymentProcessor {

  val SupportedCurrencies: Set[FiatCurrency] = Set(Euro, UsDollar)
  val MinFee = BigDecimal(0.01)
  val MaxFee = BigDecimal(2.99)

  def calculateFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] = {
    requireSupported(amount.currency)
    val num = implicitly[Numeric[BigDecimal]]
    val baseFee = amount.value * 0.005
    val fee = num.min(num.max(baseFee, MinFee), MaxFee)
    roundUp(fee, amount.currency)
  }

  override def bestStepSize[C <: FiatCurrency](currency: C): CurrencyAmount[C] = {
    requireSupported(currency)
    CurrencyAmount(2, currency)
  }

  private def roundUp[C <: FiatCurrency](amount: BigDecimal, currency: C): CurrencyAmount[C] =
    CurrencyAmount(amount.setScale(currency.precision, RoundingMode.UP), currency)

  private def requireSupported(currency: FiatCurrency): Unit = {
    require(SupportedCurrencies.contains(currency),
      s"$currency is not supported, use one of: ${SupportedCurrencies.mkString(", ")}")
  }
}
