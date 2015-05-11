package coinffeine.headless.parsing

import org.scalatest.OptionValues

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._
import coinffeine.model.order.Price

class AmountsParserTest extends UnitTest with OptionValues {

  "The amounts parser" should "recognize bitcoin amounts up to 8 decimals" in {
    AmountsParser.BitcoinAmount.unapply("1.23456789BTC").value shouldBe 1.23456789.BTC
    AmountsParser.BitcoinAmount.unapply("100BTC").value shouldBe 100.BTC
  }

  it should "reject amounts with more than 8 decimals or non numeric" in {
    AmountsParser.BitcoinAmount.unapply("1.234567890BTC") shouldBe 'empty
    AmountsParser.BitcoinAmount.unapply("one bitcoin") shouldBe 'empty
  }

  it should "be case insensitive with respect to the unit" in {
    AmountsParser.BitcoinAmount.unapply("100btc").value shouldBe 100.BTC
  }

  it should "allow monetary unit omission" in {
    AmountsParser.BitcoinAmount.unapply("100").value shouldBe 100.BTC
  }

  it should "recognize en Euro amount up to two decimals" in {
    AmountsParser.FiatAmount.unapply("123EUR").value shouldBe 123.EUR
    AmountsParser.FiatAmount.unapply("123.4EUR").value shouldBe 123.4.EUR
    AmountsParser.FiatAmount.unapply("123.45EUR").value shouldBe 123.45.EUR
  }

  it should "reject fiat amounts with more decimals than the currency precision" in {
    AmountsParser.FiatAmount.unapply("123.456EUR") shouldBe 'empty
  }

  it should "require a valid fiat currency symbol" in {
    AmountsParser.FiatAmount.unapply("123") shouldBe 'empty
    AmountsParser.FiatAmount.unapply("123BTC") shouldBe 'empty
    AmountsParser.FiatAmount.unapply("123XYZ") shouldBe 'empty
  }

  it should "reject invalid fiat amounts" in {
    AmountsParser.FiatAmount.unapply("one euro and a half") shouldBe 'empty
  }

  it should "recognize all supported fiat currencies regardless of casing" in {
    AmountsParser.FiatAmount.unapply("1eur").value shouldBe 1.EUR
    AmountsParser.FiatAmount.unapply("2.34USD").value shouldBe 2.34.USD
    AmountsParser.FiatAmount.unapply("2.34usd").value shouldBe 2.34.USD
  }

  it should "recognize prices with exchanged units specified" in {
    AmountsParser.Price.unapply("10EUR/BTC").value shouldBe Price(10.EUR)
    AmountsParser.Price.unapply("10USD/BTC").value shouldBe Price(10.USD)
  }

  it should "recognize prices without the '/BTC' suffix" in {
    AmountsParser.Price.unapply("10EUR").value shouldBe Price(10.EUR)
    AmountsParser.Price.unapply("10USD").value shouldBe Price(10.USD)
  }

  it should "recognize all supported fiat currencies in prices regardless of casing" in {
    AmountsParser.Price.unapply("10eur").value shouldBe Price(10.EUR)
    AmountsParser.Price.unapply("10usd").value shouldBe Price(10.USD)
  }

  it should "recognize prices with more precision that the underlying fiat currency" in {
    AmountsParser.Price.unapply("1.2345eur").value shouldBe Price(1.2345, Euro)
  }
}
