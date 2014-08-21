package coinffeine.peer.amounts

import scala.math.BigDecimal.RoundingMode

import coinffeine.model.currency.{BitcoinAmount, CurrencyAmount, FiatCurrency}
import coinffeine.model.exchange.{Both, BuyerRole, SellerRole}
import coinffeine.model.market.{Ask, Bid, Order}
import coinffeine.model.payment.OkPayPaymentProcessor

private[amounts] class DefaultOrderFundsCalculator extends OrderFundsCalculator {
  import coinffeine.peer.amounts.DefaultOrderFundsCalculator._

  override def calculateFunds[C](
    order: Order[FiatCurrency]): (CurrencyAmount[FiatCurrency], BitcoinAmount) = {

    val role = order.orderType match {
      case Bid => BuyerRole
      case Ask => SellerRole
    }

    val bitcoinAmount = order.amount * role.select(ProportionOfBitcoinToBlock)

    if(role == BuyerRole) {
      val fiatAmount = fiatAmountPlusFee(order.price * order.amount.value)
      (fiatAmount, bitcoinAmount)
    } else {
      (order.fiatAmount.currency.Zero , bitcoinAmount)
    }
  }

  private def calculateFiatFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] = {
    val steps = calculateSteps(amount)
    val feeByStep = roundUp(
      OkPayPaymentProcessor.calculateFee(amount / steps).value, amount.currency)
    feeByStep * steps
  }

  private def fiatAmountPlusFee[C <: FiatCurrency](amount: CurrencyAmount[C]): CurrencyAmount[C] = {
    amount + calculateFiatFee(amount)
  }

  private def calculateSteps[C <: FiatCurrency](amount: CurrencyAmount[C]) = 10

  private def roundUp[C <: FiatCurrency](amount: BigDecimal, currency: C): CurrencyAmount[C] =
    currency(amount.setScale(currency.precision, RoundingMode.UP))
}

object DefaultOrderFundsCalculator {

  /** Bitcoins to block as a proportion of the amount to be transferred */
  private val ProportionOfBitcoinToBlock = Both[BigDecimal](buyer = 0.2, seller = 1.1)
}
