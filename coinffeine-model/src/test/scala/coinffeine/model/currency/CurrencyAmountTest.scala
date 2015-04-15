package coinffeine.model.currency

import java.io._

import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest

class CurrencyAmountTest extends UnitTest with PropertyChecks {

  val sampleAmounts = Table[CurrencyAmount[_ <: Currency]]("currency amount",
    Euro.Zero,
    Euro(0.01),
    Euro(10.00),
    UsDollar(1234.56),
    UsDollar(-3.23),
    Bitcoin.Zero,
    Bitcoin(1.000123),
    Bitcoin.fromSatoshi(1)
  )

  "A currency amount" should "be converted to a big integer and back into a currency amount" in {
    forAll (sampleAmounts) { amount =>
      CurrencyAmount.exactAmount(amount.value, amount.currency) shouldBe amount
    }
  }

  it should "be serializable" in {
    forAll (sampleAmounts) { amount =>
      val stream = new ByteArrayOutputStream()
      val writer = new ObjectOutputStream(stream)
      writer.writeObject(amount)
      val reader = new ObjectInputStream(new ByteArrayInputStream(stream.toByteArray))
      reader.readObject() shouldBe amount
    }
  }

  it should "be numeric for statically known currencies" in {
    10.BTC - 20.BTC should be <= Bitcoin.Zero
    1.USD + UsDollar.numeric.fromInt(150) shouldBe 2.5.USD
    Seq(1.EUR, 2.EUR, 3.EUR).sum shouldBe 6.EUR
  }

  it should "be numeric for currencies handled generically" in {
    forAll(sampleAmounts) { amount =>
      genericHandling(amount)
    }
  }

  it should "use the whole precision when converted to string" in {
    forAll(sampleAmounts) { amount =>
      val valueRepresentation = amount.format(Currency.NoSymbol)
      valueRepresentation.reverse.takeWhile(_.isDigit) should have size amount.currency.precision
    }
  }

  it should "be printable" in {
    Euro.Zero.toString shouldBe "€0.00"
    0.01.USD.toString shouldBe "USD0.01"
    -2.2.EUR.toString shouldBe "€-2.20"
    Bitcoin.fromSatoshi(-1).toString shouldBe "-0.00000001BTC"
  }

  private def genericHandling[C <: Currency](amount: CurrencyAmount[C]): Unit = {
    implicit val numeric = amount.numeric
    amount + numeric.fromInt(1) should be > amount
  }
}
