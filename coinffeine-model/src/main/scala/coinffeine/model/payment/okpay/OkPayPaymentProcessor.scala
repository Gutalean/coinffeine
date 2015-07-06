package coinffeine.model.payment.okpay

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency._
import coinffeine.model.payment.PaymentProcessor

object OkPayPaymentProcessor extends PaymentProcessor {

  val SupportedCurrencies: Set[FiatCurrency] = Set(Euro, UsDollar)
  val MinFee = BigDecimal(0.01)
  val MaxFee = BigDecimal(2.99)

  def calculateFee(amount: FiatAmount): FiatAmount = {
    requireSupported(amount.currency)
    val num = implicitly[Numeric[BigDecimal]]
    val baseFee = amount.value * 0.005
    val fee = num.min(num.max(baseFee, MinFee), MaxFee)
    roundUp(fee, amount.currency)
  }

  override def bestStepSize(currency: FiatCurrency): FiatAmount = {
    requireSupported(currency)
    currency(2)
  }

  private def roundUp(amount: BigDecimal, currency: FiatCurrency): FiatAmount =
    currency.exactAmount(amount.setScale(currency.precision, RoundingMode.UP))

  private def requireSupported(currency: FiatCurrency): Unit = {
    require(SupportedCurrencies.contains(currency),
      s"$currency is not supported, use one of: ${SupportedCurrencies.mkString(", ")}")
  }
}
