package coinffeine.model.currency

import coinffeine.common.test.UnitTest

class BitcoinAmountTest extends UnitTest with CurrencyAmountBehaviors {

  "Bitcoin amount" should behave like aCurrencyAmount(Bitcoin)

  it should "be numeric" in {
    10.BTC - 20.BTC should be <= Bitcoin.zero
    1.BTC + BitcoinAmount.Numeric.fromInt(150000000) shouldBe 2.5.BTC
    Seq(1.BTC, 2.BTC, 3.BTC).sum shouldBe 6.BTC
  }

  it should "be printable" in {
    Bitcoin.zero.toString shouldBe "0.00000000BTC"
    0.01.BTC.toString shouldBe "0.01000000BTC"
    Bitcoin.fromSatoshi(-1).toString shouldBe "-0.00000001BTC"
  }
}
