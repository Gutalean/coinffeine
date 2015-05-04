package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList
import scalaz.syntax.std.option._
import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.amounts.DefaultAmountsComponent

class MaximumFiatValidationTest extends UnitTest with Inside with DefaultAmountsComponent {

  private val instance = new MaximumFiatValidation(amountsCalculator)

  "Maximum fiat requirement" should "reject orders above the limit" in {
    val limitPrice = LimitPrice(amountsCalculator.maxFiatPerExchange(Euro))
    inside(instance.apply(OrderRequest(Bid, 1.1.BTC, limitPrice), Spread.empty)) {
      case Error(NonEmptyList(requirement)) =>
        requirement should include ("Maximum allowed fiat amount")
    }

    val spread = Spread(lowestAsk = limitPrice.limit.some, highestBid = None)
    inside(instance.apply(OrderRequest(Bid, 1.1.BTC, MarketPrice(Euro)), spread)) {
      case Error(NonEmptyList(requirement)) =>
        requirement should include ("Maximum allowed fiat amount")
    }
  }

  it should "accept orders up to the limit" in {
    instance.apply(OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR)), Spread.empty) shouldBe OK
  }
}
