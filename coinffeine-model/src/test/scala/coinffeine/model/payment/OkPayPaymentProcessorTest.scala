package coinffeine.model.payment

import java.util.Currency

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class OkPayPaymentProcessorTest extends UnitTest {

  "OkPay" should "compute payment fees" in {
    OkPayPaymentProcessor.calculateFee(50.EUR) shouldBe 0.25.EUR
    OkPayPaymentProcessor.calculateFee(50.USD) shouldBe 0.25.USD
  }

  it should "have a minimum fee of 0.01" in {
    OkPayPaymentProcessor.calculateFee(1.EUR) shouldBe 0.01.EUR
    OkPayPaymentProcessor.calculateFee(1.USD) shouldBe 0.01.USD
  }

  it should "have a maximum fee of 2.99" in {
    OkPayPaymentProcessor.calculateFee(1000.EUR) shouldBe 2.99.EUR
    OkPayPaymentProcessor.calculateFee(1000.USD) shouldBe 2.99.USD
  }

  it should "round up fees to the currency precision" in {
    OkPayPaymentProcessor.calculateFee(3.EUR) shouldBe 0.02.EUR
    OkPayPaymentProcessor.calculateFee(3.USD) shouldBe 0.02.USD
  }

  it should "compute the best step size" in {
    OkPayPaymentProcessor.bestStepSize(Euro) shouldBe 2.EUR
    OkPayPaymentProcessor.bestStepSize(UsDollar) shouldBe 2.USD
  }

  it should "reject unsupported currencies" in {
    object InventedCurrency extends FiatCurrency {
      override val javaCurrency = Currency.getInstance("XXX")
      override val precision = 2
    }
    an [IllegalArgumentException] shouldBe thrownBy {
      OkPayPaymentProcessor.calculateFee(InventedCurrency(3))
    }
  }
}
