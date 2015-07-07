package coinffeine.peer.config

import com.typesafe.config.ConfigFactory
import org.scalacheck.{Gen, Arbitrary}
import scalaz.syntax.std.boolean._
import org.scalacheck.Arbitrary._
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.currency._

class FiatAmountsMappingTest extends UnitTest with PropertyChecks {

  "A fiat amounts mapping" should "represent empty fiat amounts" in {
    FiatAmountsMapping.toConfig(FiatAmounts.empty) shouldBe ConfigFactory.empty
  }

  it should "represent every amount as a different key" in {
    val config = FiatAmountsMapping.toConfig(FiatAmounts.fromAmounts(1.EUR, 1.95.USD))
    config.getDouble("EUR") shouldBe 1
    config.getDouble("USD") shouldBe 1.95
    config.hasPath("GBP") shouldBe false
  }

  it should "parse empty configs as empty fiat amounts" in {
    FiatAmountsMapping.fromConfig(ConfigFactory.empty) shouldBe FiatAmounts.empty
  }

  it should "represent fiat amounts as a subconfig of an existing one" in {
    val originalConfig = ConfigFactory.parseString(
      """
        |foo = true
        |bar = 10
      """.stripMargin)
    val config = FiatAmountsMapping.toConfig(
      FiatAmounts.fromAmounts(1.EUR), "amounts", originalConfig)
    config shouldBe ConfigFactory.parseString(
      """
        |foo = true
        |bar = 10
        |amounts = {
        |  EUR = 1
        |}""".stripMargin)
  }

  it should "parse configs with some fiat amounts" in {
    FiatAmountsMapping.fromConfig(ConfigFactory.parseString(
      """
        |EUR = 1
        |USD = 2.5
      """.stripMargin)) shouldBe FiatAmounts.fromAmounts(1.EUR, 2.5. USD)
  }

  it should "parse a missing path as a missing fiat amounts" in {
    val nonExisting = ConfigFactory.empty
    FiatAmountsMapping.fromConfig("amounts", nonExisting) shouldBe 'empty
  }

  it should "parse an existing empty path as empty fiat amounts" in {
    val existingButEmpty = ConfigFactory.parseString("amounts = {}")
    FiatAmountsMapping.fromConfig("amounts", existingButEmpty) shouldBe Some(FiatAmounts.empty)
  }


  def maybeFiatAmount(currency: FiatCurrency): Gen[Option[FiatAmount]] = for {
    present <- arbitrary[Boolean]
    cents <- Gen.choose(-10000, 10000)
  } yield present.option(currency.fromUnits(cents))

  val fiatAmounts = for {
    eur <- maybeFiatAmount(Euro)
    usd <- maybeFiatAmount(UsDollar)
  } yield FiatAmounts(Seq(eur, usd).flatten)

  it should "support roundtrip mapping" in {
    forAll(fiatAmounts) { amounts =>
      FiatAmountsMapping.fromConfig(FiatAmountsMapping.toConfig(amounts)) shouldBe amounts
    }
  }

  it should "support roundtrip mapping when being a subpath of a config" in {
    val originalConfig = ConfigFactory.parseString(
      """someSetting = true
        |otherSetting = 10
      """.stripMargin)
    forAll(fiatAmounts) { amounts =>
      val config = FiatAmountsMapping.toConfig(amounts, "amounts", originalConfig)
      FiatAmountsMapping.fromConfig("amounts", config) shouldBe Some(amounts)
    }
  }
}
