package coinffeine.overlay.relay

import akka.util.{ByteString, ByteStringBuilder}
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest

class IntSerializationTest extends UnitTest with PropertyChecks {

  private val bytes = Choose.chooseByte.choose(Byte.MinValue, Byte.MaxValue)
  private val byteStringsOf4 = Gen.containerOfN[Array, Byte](4, bytes).map(ByteString.apply)

  "Int serialization" should "support roundtrip serialization" in {
    forAll { value: Int =>
      IntSerialization.deserialize(serialize(value)) shouldBe value
    }
    forAll(byteStringsOf4) { bytes =>
      serialize(IntSerialization.deserialize(bytes)) shouldBe bytes
    }
  }

  def serialize(value: Int): ByteString = {
    val output = new ByteStringBuilder
    IntSerialization.serialize(value, output)
    output.result()
  }
}
