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
    val tooBigBid = Order.random(Bid, 1.1.BTC, Price(amountsCalculator.maxFiatPerExchange(Euro)))
    inside(instance.apply(tooBigBid)) {
      case Error(NonEmptyList(requirement)) =>
        requirement.description should include ("Maximum allowed fiat amount")
    }
  }

  it should "accept orders up to the limit" in {
    val newBid = Order.random(Bid, 0.5.BTC, Price(300.EUR))
    instance.apply(newBid) shouldBe OK
  }
}
