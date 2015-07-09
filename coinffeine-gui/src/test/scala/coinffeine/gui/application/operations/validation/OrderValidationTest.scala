package coinffeine.gui.application.operations.validation

import scalaz.NonEmptyList
import scalaz.syntax.semigroup._

import coinffeine.common.test.UnitTest
import coinffeine.gui.application.operations.validation.OrderValidation._

class OrderValidationTest extends UnitTest {

  private val selfCross = "thou not self-cross!"
  private val offLimits = "too big order"
  private val noFiat = "you have no Euros but you need 3.34 €"
  private val noBtc = "you have 0 BTC but you need 3 satoshis"

  private val offLimitsError: Problem = Error(NonEmptyList(offLimits))
  private val selfCrossError: Problem = Error(NonEmptyList(selfCross))
  private val noFiatWarning: Problem = Warning(NonEmptyList(noFiat))
  private val noBtcWarning: Problem = Warning(NonEmptyList(noBtc))

  "Combining check results" should "treat Error as dominant" in {
    noFiatWarning |+| offLimitsError shouldBe offLimitsError
  }

  it should "combine errors" in {
    offLimitsError |+| selfCrossError shouldBe Error(NonEmptyList(offLimits, selfCross))
  }

  it should "combine warnings" in {
    noFiatWarning |+| noBtcWarning shouldBe Warning(NonEmptyList(noFiat, noBtc))
  }

  it should "combine multiple results" in {
    selfCrossError |+| noFiatWarning |+| noBtcWarning shouldBe selfCrossError
  }
}
