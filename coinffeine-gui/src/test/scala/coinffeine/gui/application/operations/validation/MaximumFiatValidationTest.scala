package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import org.scalatest.Inside

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.amounts.DefaultAmountsComponent

class MaximumFiatValidationTest extends UnitTest with Inside with DefaultAmountsComponent {

  private val instance = new MaximumFiatValidation(amountsCalculator)

  "Maximum fiat requirement" should "reject orders above the limit" in {
    val limit = LimitPrice(amountsCalculator.maxFiatPerExchange(Euro))
    inside(instance.apply(OrderRequest(Bid, 1.1.BTC, limit))) {
      case Error(NonEmptyList(requirement)) =>
        requirement should include ("Maximum allowed fiat amount")
    }
  }

  it should "accept orders up to the limit" in {
    instance.apply(OrderRequest(Bid, 0.5.BTC, LimitPrice(300.EUR))) shouldBe OK
  }
}
