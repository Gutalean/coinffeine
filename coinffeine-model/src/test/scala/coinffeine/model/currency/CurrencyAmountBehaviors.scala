package coinffeine.model.currency

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest

trait CurrencyAmountBehaviors extends PropertyChecks { this: UnitTest =>

  def aCurrencyAmount(currency: Currency): Unit = {

    val sampleAmounts = Table[currency.Amount]("currency amount",
      currency.zero,
      currency.exactAmount(0.1),
      currency.exactAmount(10),
      currency.exactAmount(1234.56),
      currency.exactAmount(-3.23)
    )

    it should "be converted to a big integer and back into a currency amount" in {
      forAll (sampleAmounts) { amount =>
        amount.currency.exactAmount(amount.value) shouldBe amount
      }
    }

    it should "be taken its absolute value" in {
      forAll (sampleAmounts) { amount =>
        if (amount.isPositive) {
          amount.abs shouldBe amount
        } else {
          amount.abs shouldBe -amount
        }
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

    it should "use the whole precision when converted to string" in {
      forAll(sampleAmounts) { amount =>
        val valueRepresentation = amount.format(Currency.NoSymbol)
        valueRepresentation.reverse.takeWhile(_.isDigit) should have size amount.currency.precision
      }
    }
  }
}
