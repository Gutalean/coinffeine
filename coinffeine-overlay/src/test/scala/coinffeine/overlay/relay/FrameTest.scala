package coinffeine.overlay.relay

import akka.util.{ByteStringBuilder, ByteString}
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.overlay.relay.Frame.{IncompleteInput, Parsed, FailedParsing}

class FrameTest extends UnitTest with PropertyChecks {

  private val sampleFrame = Frame(ByteString("hello"))

  "A frame" should "not be parsed if the magic number differs" in {
    val wrongMagicNumber: Byte = 42
    val serializedFrame = wrongMagicNumber +: sampleFrame.serialize.drop(1)
    Frame.deserialize(serializedFrame) shouldBe
      FailedParsing(s"Bad magic number: 42 instead of ${Frame.MagicByte}")
  }

  it should "not be parsed if the length is negative" in {
    val builder = new ByteStringBuilder
    builder.putByte(Frame.MagicByte)
    IntSerialization.serialize(-1, builder)
    val invalidFrame = builder.result()
    Frame.deserialize(invalidFrame) shouldBe FailedParsing(s"Invalid length of -1")
  }

  it should "support roundtrip serialization" in {
    forAll { payload: Array[Byte] =>
      val originalFrame = Frame(ByteString(payload))
      val serializedFrame = originalFrame.serialize
      val deserializedFrame = Frame.deserialize(serializedFrame)
      deserializedFrame shouldBe Parsed(originalFrame, ByteString.empty)
    }
  }

  it should "not consume more bytes than necessary" in {
    forAll { extraBytes: Array[Byte] =>
      Frame.deserialize(sampleFrame.serialize ++ extraBytes) shouldBe
        Parsed(sampleFrame, ByteString(extraBytes))
    }
  }

  it should "wait for more input if the frame is incomplete" in {
    val serializedFrame = sampleFrame.serialize
    for (missingBytes <- 1 to serializedFrame.size - 1) {
      Frame.deserialize(serializedFrame.dropRight(missingBytes)) shouldBe IncompleteInput
    }
  }
}
