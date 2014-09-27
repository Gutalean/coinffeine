package coinffeine.model.currency

import java.io._

import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.currency.Currency.{Bitcoin, UsDollar, Euro}

class CurrencyAmountTest extends UnitTest with PropertyChecks {

  val sampleAmounts = Table("currency amount",
    Euro(0.01),
    Euro(10.00),
    UsDollar(1234.56),
    UsDollar(-3.23),
    Bitcoin.Zero,
    Bitcoin(1.000123),
    Bitcoin.fromSatoshi(1)
  )

  "A currency amount" should "be converted to indivisible units and back into a currency amount" in {
    forAll (sampleAmounts) { amount =>
      CurrencyAmount.fromIndivisibleUnits(amount.toIndivisibleUnits, amount.currency) shouldBe amount
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
}
