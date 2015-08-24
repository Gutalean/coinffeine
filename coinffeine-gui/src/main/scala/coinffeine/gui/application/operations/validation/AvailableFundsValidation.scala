package coinffeine.gui.application.operations.validation

import scalaz.syntax.applicative._

import coinffeine.common.properties.Property
import coinffeine.model.currency._
import coinffeine.model.currency.balance.{FiatBalances, BitcoinBalance}
import coinffeine.model.market.Spread
import coinffeine.model.order.OrderRequest
import coinffeine.model.util.Cached
import coinffeine.peer.amounts.AmountsCalculator

private class AvailableFundsValidation(
    amountsCalculator: AmountsCalculator,
    fiatBalances: Property[Cached[FiatBalances]],
    bitcoinBalance: Property[Option[BitcoinBalance]]) extends OrderValidation {

  override def apply(
      request: OrderRequest,
      spread: Spread): OrderValidation.Result =
    checkAvailableFunds(
      currentAvailableFiat(request.price.currency), currentAvailableBitcoin(), request, spread)

  private def currentAvailableFiat(currency: FiatCurrency): Option[FiatAmount] = {
    val cachedBalances = fiatBalances.get
    for {
      balance <- cachedBalances.cached.amounts.get(currency)
      if cachedBalances.status.isFresh
    } yield balance
  }

  private def currentAvailableBitcoin(): Option[BitcoinAmount] =
    bitcoinBalance.get.map(_.available)

  private def checkAvailableFunds(
      availableFiat: Option[FiatAmount],
      availableBitcoin: Option[BitcoinAmount],
      request: OrderRequest,
      spread: Spread): OrderValidation.Result =
    amountsCalculator.estimateAmountsFor(request, spread).fold(OrderValidation.Ok) {
      estimatedAmounts =>
        checkForAvailableBalance("bitcoin", availableBitcoin,
          estimatedAmounts.bitcoinRequired(request.orderType)) *>
        checkForAvailableBalance(request.price.currency.toString, availableFiat,
          estimatedAmounts.fiatRequired(request.orderType))
    }

  private def checkForAvailableBalance[A <: CurrencyAmount[A]](
      balanceName: String, available: Option[A], required: A) =
    available match {
      case Some(enoughFunds) if enoughFunds >= required => OrderValidation.Ok
      case Some(notEnoughFunds) => shortOfFunds(notEnoughFunds, required)
      case None => cannotCheckBalance(balanceName)
    }

  private def shortOfFunds[A <: CurrencyAmount[A]](available: A, required: A) =
    OrderValidation.warning(s"Your $available available are insufficient for this order " +
        s"(at least $required required).\nYou may proceed, but your order will be stalled " +
        "until enough funds are available.")

  private def cannotCheckBalance(name: String) = OrderValidation.warning(
    s"It is not possible to check your $name balance.\n" +
        "It can be submitted anyway, but it might be stalled until your balance is " +
        "available again and it has enough funds to satisfy the order.")
}
