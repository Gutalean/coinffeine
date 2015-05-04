package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation.Result._
import coinffeine.gui.application.operations.validation.OrderValidation._

class OrderValidationTest extends UnitTest {

  private val selfCross = "thou not self-cross!"
  private val offLimits = "too big order"
  private val noFiat = "you have no Euros but you need 3.34 €"
  private val noBtc = "you have 0 BTC but you need 3 satoshis"

  "Combining check results" should "treat RequirementsMet as recessive" in {
    val otherResults = Seq(
      OK,
      Error(NonEmptyList(selfCross)),
      Warning(NonEmptyList(noFiat))
    )
    for (other <- otherResults) {
      combine(other, OK) shouldBe other
      combine(OK, other) shouldBe other
    }
  }

  it should "treat RequirementsUnmet as dominant" in {
    val unmet = Error(NonEmptyList(offLimits))
    for (other <- Seq(
      OK,
      Warning(NonEmptyList(noFiat))
    )) {
      combine(other, unmet) shouldBe unmet
      combine(unmet, other) shouldBe unmet
    }
  }

  it should "combine requirements unmet" in {
    combine(Error(NonEmptyList(offLimits)),
      Error(NonEmptyList(selfCross))) shouldBe
      Error(NonEmptyList(offLimits, selfCross))
  }

  it should "combine optional requirements unmet" in {
    combine(Warning(NonEmptyList(noFiat)),
      Warning(NonEmptyList(noBtc))) shouldBe
      Warning(NonEmptyList(noFiat, noBtc))
  }

  it should "consider 0 requirements as met" in {
    combine() shouldBe OK
  }

  it should "combine multiple results" in {
    combine(
      OK,
      Error(NonEmptyList(selfCross)),
      Warning(NonEmptyList(noFiat))
    ) shouldBe Error(NonEmptyList(selfCross))
  }
}
