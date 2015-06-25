package coinffeine.model.currency

import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest

class FiatAmountTest extends UnitTest with PropertyChecks {

  implicit val arbitraryFiatAmount = Arbitrary(for {
    currency <- Gen.oneOf(Euro, UsDollar)
    units <- arbitrary[Int]
  } yield currency.fromUnits(units))

  "Fiat amount addition" should behave like workingForSameCurrencyValues(_ + _)
  "Fiat amount subtraction" should behave like workingForSameCurrencyValues(_ - _)
  "Fiat amount max" should behave like workingForSameCurrencyValues(_ max _)
  "Fiat amount min" should behave like workingForSameCurrencyValues(_ min _)
  "Fiat amount averaging" should behave like workingForSameCurrencyValues(_ averageWith _)
  "Fiat amount comparison" should behave like workingForSameCurrencyValues(_ compare _)

  "Fiat amount division with remainder" should "work for same-currency values" in {
    forAll { (left: FiatAmount, right: FiatAmount) =>
      whenever(right.units != 0) {
        expectExceptionForDifferentCurrencies(_ /% _, left, right)
      }
    }
  }

  def workingForSameCurrencyValues(op: (FiatAmount, FiatAmount) => Any): Unit = {
    it should "work for same-currency values" in {
      forAll { (left: FiatAmount, right: FiatAmount) =>
        expectExceptionForDifferentCurrencies(op, left, right)
      }
    }
  }

  private def expectExceptionForDifferentCurrencies(
      op: (FiatAmount, FiatAmount) => Any, left: FiatAmount, right: FiatAmount): Unit = {
    if (left.currency == right.currency) {
      noException shouldBe thrownBy { op(left, right) }
    } else {
      an[IllegalArgumentException] shouldBe thrownBy { op(left, right) }
    }
  }
}
