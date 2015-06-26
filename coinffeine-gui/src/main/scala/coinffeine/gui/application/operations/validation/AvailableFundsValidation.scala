package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.common.properties.Property
import coinffeine.model.currency._
import coinffeine.model.currency.balance.{BitcoinBalance, CachedFiatBalances}
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest
import coinffeine.peer.amounts.AmountsCalculator

private class AvailableFundsValidation(
    amountsCalculator: AmountsCalculator,
    fiatBalances: Property[CachedFiatBalances],
    bitcoinBalance: Property[Option[BitcoinBalance]]) extends OrderValidation {

  override def apply(
      request: OrderRequest,
      spread: Spread): OrderValidation.Result =
    checkAvailableFunds(
      currentAvailableFiat(request.price.currency), currentAvailableBitcoin(), request, spread)

  private def currentAvailableFiat(currency: FiatCurrency): Option[FiatAmount] = {
    val cachedBalances = fiatBalances.get
    for {
      balance <- cachedBalances.value.get(currency)
      if cachedBalances.status.isFresh
    } yield balance.amount
  }

  private def currentAvailableBitcoin(): Option[BitcoinAmount] =
    bitcoinBalance.get.filter(_.status.isFresh).map(_.available)

  private def checkAvailableFunds(
      availableFiat: Option[FiatAmount],
      availableBitcoin: Option[BitcoinAmount],
      request: OrderRequest,
      spread: Spread): OrderValidation.Result =
    amountsCalculator.estimateAmountsFor(request, spread)
        .fold[OrderValidation.Result](OrderValidation.OK) { estimatedAmounts =>
      OrderValidation.Result.combine(
        checkForAvailableBalance("bitcoin", availableBitcoin,
          estimatedAmounts.bitcoinRequired(request.orderType)),
        checkForAvailableBalance(request.price.currency.toString, availableFiat,
          estimatedAmounts.fiatRequired(request.orderType))
      )
    }

  private def checkForAvailableBalance[A <: CurrencyAmount[A]](
      balanceName: String, available: Option[A], required: A) = {
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

  private def shortOfFunds[A <: CurrencyAmount[A]](available: A, required: A) =
    s"Your $available available are insufficient for this order " +
        s"(at least $required required).\nYou may proceed, but your order will be stalled " +
        "until enough funds are available."
}
