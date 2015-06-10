package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.common.properties.{PropertyMap, Property}
import coinffeine.model.currency._
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest
import coinffeine.peer.amounts.AmountsCalculator

private class AvailableFundsValidation(
    amountsCalculator: AmountsCalculator,
    fiatBalance: PropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]],
    bitcoinBalance: Property[Option[BitcoinBalance]]) extends OrderValidation {

  override def apply[C <: FiatCurrency](request: OrderRequest[C],
                                        spread: Spread[C]): OrderValidation.Result =
    checkAvailableFunds(
      currentAvailableFiat(request.price.currency), currentAvailableBitcoin(), request, spread)

  private def currentAvailableFiat[C <: FiatCurrency](currency: C): Option[CurrencyAmount[C]] =
    fiatBalance.get(currency)
      .filterNot(_.hasExpired)
      .map(_.amount.asInstanceOf[CurrencyAmount[C]])

  private def currentAvailableBitcoin(): Option[Bitcoin.Amount] =
    bitcoinBalance.get.filterNot(_.hasExpired).map(_.available)

  private def checkAvailableFunds[C <: FiatCurrency](
      availableFiat: Option[CurrencyAmount[C]],
      availableBitcoin: Option[Bitcoin.Amount],
      request: OrderRequest[C],
      spread: Spread[C]): OrderValidation.Result =
    amountsCalculator.estimateAmountsFor(request, spread)
      .fold[OrderValidation.Result](OrderValidation.OK) { estimatedAmounts =>
      OrderValidation.Result.combine(
        checkForAvailableBalance("bitcoin", availableBitcoin,
          estimatedAmounts.bitcoinRequired(request.orderType)),
        checkForAvailableBalance(request.price.currency.toString, availableFiat,
          estimatedAmounts.fiatRequired(request.orderType))
      )
    }

  private def checkForAvailableBalance[C <: Currency](
      balanceName: String, available: Option[CurrencyAmount[C]], required: CurrencyAmount[C]) = {
    available match {
      case Some(enoughFunds) if enoughFunds >= required => OrderValidation.OK
      case Some(notEnoughFunds) =>
        OrderValidation.Warning(NonEmptyList(shortOfFunds(notEnoughFunds, required)))
      case None =>
        OrderValidation.Warning(NonEmptyList(cannotCheckBalance(balanceName)))
    }
  }

  private def cannotCheckBalance(name: String) =
    s"It is not possible to check your $name balance.\n" +
      "It can be submitted anyway, but it might be stalled until your balance is " +
      "available again and it has enough funds to satisfy the order."

  private def shortOfFunds[C <: Currency](available: CurrencyAmount[C],
                                          required: CurrencyAmount[C]) =
    s"Your $available available are insufficient for this order " +
      s"(at least $required required).\nYou may proceed, but your order will be stalled until " +
      "enough funds are available."
}
