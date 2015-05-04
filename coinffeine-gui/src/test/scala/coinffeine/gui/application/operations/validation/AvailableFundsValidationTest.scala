package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.model.properties.{MutableProperty, MutablePropertyMap}
import coinffeine.peer.amounts.DefaultAmountsComponent

class AvailableFundsValidationTest extends UnitTest with DefaultAmountsComponent with Inside {

  private val newBid = OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR))

  "The available funds requirement" should "optionally require bitcoin funds to be known" in
    new Fixture {
      bitcoinBalance.set(Some(initialBitcoinBalance.copy(hasExpired = true)))
      inside(instance.apply(newBid)) {
        case Warning(NonEmptyList(requirement)) =>
          requirement should include ("not possible to check")
      }
      bitcoinBalance.set(None)
      instance.apply(newBid) should not be OK
    }

  it should "optionally require available bitcoin balance to cover order needs" in new Fixture {
    val notEnoughBitcoin = 0.001.BTC
    bitcoinBalance.set(Some(initialBitcoinBalance.copy(available = notEnoughBitcoin)))
    inside(instance.apply(newBid)) {
      case Warning(NonEmptyList(requirement)) =>
        requirement should include (
          s"Your $notEnoughBitcoin available are insufficient for this order")
    }
  }

  it should "optionally require fiat funds to be known" in new Fixture {
    fiatBalance.set(Euro, initialFiatBalance.copy(hasExpired = true))
    inside(instance.apply(newBid)) {
      case Warning(NonEmptyList(requirement)) =>
        requirement should include ("not possible to check")
    }
  }

  it should "optionally require available fiat balance to cover order needs" in new Fixture {
    val notEnoughFiat = 1.EUR
    fiatBalance.set(Euro, initialFiatBalance.copy(amount = notEnoughFiat))
    inside(instance.apply(newBid)) {
      case Warning(NonEmptyList(requirement)) =>
        requirement should include (
          s"Your $notEnoughFiat available are insufficient for this order")
    }
  }

  private trait Fixture {
    val fiatBalance = new MutablePropertyMap[FiatCurrency, FiatBalance[_ <: FiatCurrency]]()
    val initialFiatBalance = FiatBalance(amount = 450.EUR, hasExpired = false)
    fiatBalance.set(Euro, initialFiatBalance)
    val initialBitcoinBalance = BitcoinBalance(
      estimated = 2.3.BTC,
      available = 2.3.BTC,
      minOutput = Some(0.1.BTC),
      hasExpired = false
    )
    val bitcoinBalance = new MutableProperty[Option[BitcoinBalance]](Some(initialBitcoinBalance))
    val instance = new AvailableFundsValidation(amountsCalculator, fiatBalance, bitcoinBalance)
  }
}
