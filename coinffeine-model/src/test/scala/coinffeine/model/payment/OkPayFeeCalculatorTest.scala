package coinffeine.model.payment

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Implicits._

class OkPayFeeCalculatorTest extends UnitTest {

  "The OkPay calculateFee" should "return the fee" in {
    OkPayFeeCalculator.calculateFee(50.EUR) should be(0.25.EUR)
    OkPayFeeCalculator.calculateFee(50.USD) should be(0.25.USD)
  }

  it should "return 0.01 for any currency as minimum fee" in {
    OkPayFeeCalculator.calculateFee(1.EUR) should be (0.01.EUR)
    OkPayFeeCalculator.calculateFee(1.USD) should be (0.01.USD)
  }

  it should "return 2.99 for any currency as minimum fee" in {
    OkPayFeeCalculator.calculateFee(1000.EUR) should be (2.99.EUR)
    OkPayFeeCalculator.calculateFee(1000.USD) should be (2.99.USD)
  }

  it should "round up to 0.02 for a fee between 0.01 and 0.02" in {
    OkPayFeeCalculator.calculateFee(3.EUR) should be (0.02.EUR)
    OkPayFeeCalculator.calculateFee(3.USD) should be (0.02.USD)
  }
}
