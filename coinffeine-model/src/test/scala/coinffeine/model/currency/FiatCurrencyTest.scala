package coinffeine.model.currency

import coinffeine.common.test.UnitTest

class FiatCurrencyTest extends UnitTest {

  "A fiat currency" should "be selected from its currency code" in {
    FiatCurrency.get("EUR") shouldBe Some(Euro)
    FiatCurrency.get("USD") shouldBe Some(UsDollar)
    FiatCurrency.get("XXX") shouldBe 'empty
  }

  it should "be selected unsafely" in {
    FiatCurrency("EUR") shouldBe Euro
    FiatCurrency("USD") shouldBe UsDollar
    an [IllegalArgumentException] shouldBe thrownBy { FiatCurrency("XXX") }
  }
}
