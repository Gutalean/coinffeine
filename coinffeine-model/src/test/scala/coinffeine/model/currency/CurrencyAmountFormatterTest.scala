package coinffeine.model.currency

import coinffeine.common.test.UnitTest

class CurrencyAmountFormatterTest extends UnitTest {

  private val instance = CurrencyAmountFormatter

  "Currency amount formatter" should "format a currency amount" in {
    instance.format(12.50.EUR) shouldBe "12.50EUR"
    instance.format(-12.50.BTC) shouldBe "-12.50000000BTC"
    instance.format(0.USD) shouldBe "0.00USD"
  }

  it should "format a currency amount with symbol position other than preferred" in {
    instance.format(12.50.EUR, Currency.SymbolPrefixed) shouldBe "EUR12.50"
    instance.format(-12.50.BTC, Currency.SymbolPrefixed) shouldBe "BTC-12.50000000"
    instance.format(0.USD, Currency.SymbolPrefixed) shouldBe "USD0.00"
  }

  it should "format missing currency amount" in {
    instance.formatMissing(Euro) shouldBe "_.__EUR"
    instance.formatMissing(UsDollar) shouldBe "_.__USD"
    instance.formatMissing(Bitcoin) shouldBe "_.________BTC"
  }

  it should "format missing currency amount with symbol position other than preferred" in {
    instance.formatMissing(Euro, Currency.SymbolPrefixed) shouldBe "EUR_.__"
    instance.formatMissing(UsDollar, Currency.SymbolPrefixed) shouldBe "USD_.__"
    instance.formatMissing(Bitcoin, Currency.SymbolPrefixed) shouldBe "BTC_.________"
  }
}
