package coinffeine.gui.application.operations.validation

import scalaz.{Failure, NonEmptyList}

import org.scalatest.Inside

import coinffeine.common.properties.MutableProperty
import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.currency.balance.BitcoinBalance
import coinffeine.model.market._
import coinffeine.model.order.{Bid, LimitPrice, OrderRequest}
import coinffeine.model.util.Cached
import coinffeine.peer.amounts.DefaultAmountsCalculator

class AvailableFundsValidationTest extends UnitTest with Inside {

  private val newBid = OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR))
  private val spread = Spread.empty

  "The available funds requirement" should "optionally require bitcoin funds to be known" in
    new Fixture {
      givenEnoughFiatFunds()
      givenUnknownBitcoinFunds()
      inside(instance.apply(newBid, spread)) {
        case Failure(Warning(NonEmptyList(requirement))) =>
          requirement should include ("not possible to check")
      }
      bitcoinBalance.set(None)
      instance.apply(newBid, spread) should not be Ok
    }

  it should "optionally require available bitcoin balance to cover order needs" in new Fixture {
    givenEnoughFiatFunds()
    givenNotEnoughBitcoinFunds()
    inside(instance.apply(newBid, spread)) {
      case Failure(Warning(NonEmptyList(requirement))) =>
        requirement should include regex "Your .* available are insufficient for this order"
    }
  }

  it should "optionally require fiat funds to be known" in new Fixture {
    givenStaleFiatFunds()
    givenEnoughBitcoinFunds()
    inside(instance.apply(newBid, spread)) {
      case Failure(Warning(NonEmptyList(requirement))) =>
        requirement should include ("not possible to check")
    }
  }

  it should "optionally require available fiat balance to cover order needs" in new Fixture {
    givenNotEnoughFiatFunds()
    givenEnoughBitcoinFunds()
    inside(instance.apply(newBid, spread)) {
      case Failure(Warning(NonEmptyList(requirement))) =>
        requirement should include regex "Your .* available are insufficient for this order"
    }
  }

  private trait Fixture {
    private val fiatBalances = new MutableProperty(Cached.fresh(FiatAmounts.empty))
    private val initialFiatBalance = Cached.fresh(FiatAmounts.fromAmounts(450.EUR))
    val initialBitcoinBalance = BitcoinBalance(
      estimated = 2.3.BTC,
      available = 2.3.BTC,
      minOutput = Some(0.1.BTC)
    )
    val bitcoinBalance = new MutableProperty[Option[BitcoinBalance]](None)
    val instance = new AvailableFundsValidation(
      new DefaultAmountsCalculator(), fiatBalances, bitcoinBalance)

    protected def givenEnoughBitcoinFunds(): Unit = {
      bitcoinBalance.set(Some(initialBitcoinBalance))
    }

    protected def givenUnknownBitcoinFunds(): Unit = {
      bitcoinBalance.set(None)
    }

    protected def givenNotEnoughBitcoinFunds(): Unit = {
      bitcoinBalance.set(Some(initialBitcoinBalance.copy(available = 0.001.BTC)))
    }

    protected def givenEnoughFiatFunds(): Unit = {
      fiatBalances.set(initialFiatBalance)
    }

    protected def givenStaleFiatFunds(): Unit = {
      fiatBalances.set(Cached.stale(initialFiatBalance.cached))
    }

    protected def givenNotEnoughFiatFunds(): Unit = {
      fiatBalances.set(Cached.fresh(FiatAmounts.fromAmounts(1.EUR)))
    }
  }
}
