package coinffeine.overlay.relay.messages

import akka.util.{ByteString, ByteStringBuilder}
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.overlay.relay.messages.Frame.{FailedParsing, IncompleteInput, Parsed}

class FrameTest extends UnitTest with PropertyChecks {

  private val sampleFrame = Frame(ByteString("hello"))
  private val maxFrameSize = 1000

  "A frame" should "not be parsed if the magic number differs" in {
    deserialize(forgeFrameHeader(magicByte = 42, payloadLength = 10)) shouldBe
      FailedParsing(s"Bad magic number: 42 instead of ${Frame.MagicByte}")
  }

  it should "not be parsed if the length is negative" in {
    deserialize(forgeFrameHeader(Frame.MagicByte, payloadLength = -1)) shouldBe
      FailedParsing(s"Invalid length of -1")
  }

  it should "reject frames larger than the maximum size" in {
    deserialize(forgeFrameHeader(Frame.MagicByte, payloadLength = 996)) shouldBe
      FailedParsing(s"Invalid length of 996")
  }

  it should "support roundtrip serialization" in {
    forAll { payload: Array[Byte] =>
      val originalFrame = Frame(ByteString(payload))
      val deserializedFrame = deserialize(originalFrame.serialize)
      deserializedFrame shouldBe Parsed(originalFrame, ByteString.empty)
    }
  }

  it should "not consume more bytes than necessary" in {
    forAll { extraBytes: Array[Byte] =>
      deserialize(sampleFrame.serialize ++ extraBytes) shouldBe
        Parsed(sampleFrame, ByteString(extraBytes))
    }
  }

  it should "wait for more input if the frame is incomplete" in {
    val serializedFrame = sampleFrame.serialize
    for (missingBytes <- 1 to serializedFrame.size - 1) {
      deserialize(serializedFrame.dropRight(missingBytes)) shouldBe IncompleteInput
    }
  }

  private def deserialize(bytes: ByteString) = Frame.deserialize(bytes, maxFrameSize)

  private def forgeFrameHeader(magicByte: Byte, payloadLength: Int): ByteString = {
    val builder = new ByteStringBuilder
    builder.putByte(magicByte)
    IntSerialization.serialize(payloadLength, builder)
    val invalidFrame = builder.result()
    invalidFrame
  }
}
