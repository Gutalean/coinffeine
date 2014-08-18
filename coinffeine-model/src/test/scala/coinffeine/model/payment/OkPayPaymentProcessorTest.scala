package coinffeine.model.payment

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Implicits._

class OkPayPaymentProcessorTest extends UnitTest {

  "The OkPay calculateFee" should "return the fee" in {
    OkPayPaymentProcessor.calculateFee(50.EUR) should be(0.25.EUR)
    OkPayPaymentProcessor.calculateFee(50.USD) should be(0.25.USD)
  }

  it should "return 0.01 for any currency as minimum fee" in {
    OkPayPaymentProcessor.calculateFee(1.EUR) should be (0.01.EUR)
    OkPayPaymentProcessor.calculateFee(1.USD) should be (0.01.USD)
  }

  it should "return 2.99 for any currency as minimum fee" in {
    OkPayPaymentProcessor.calculateFee(1000.EUR) should be (2.99.EUR)
    OkPayPaymentProcessor.calculateFee(1000.USD) should be (2.99.USD)
  }

  it should "round up to 0.02 for a fee between 0.01 and 0.02" in {
    OkPayPaymentProcessor.calculateFee(3.EUR) should be (0.02.EUR)
    OkPayPaymentProcessor.calculateFee(3.USD) should be (0.02.USD)
  }
}
