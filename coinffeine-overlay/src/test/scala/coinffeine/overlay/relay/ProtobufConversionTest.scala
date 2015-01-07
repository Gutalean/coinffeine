package coinffeine.overlay.relay

import akka.util.ByteString

import coinffeine.common.test.UnitTest
import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.ProtobufConversion.ProtobufConversionException
import coinffeine.overlay.relay.protobuf.{RelayProtobuf => proto}
import com.google.protobuf.{ByteString => ProtoByteString}

class ProtobufConversionTest extends UnitTest {

  private val overlayId = OverlayId(1)
  private val protoOverlayId = ProtoByteString.copyFrom {
    Array.fill[Byte](19)(0) :+ (1: Byte)
  }
  private val relayMessage = RelayMessage(overlayId, ByteString("hello"))
  private val protobufRelayMessage = proto.Message.newBuilder()
    .setRelayMessage(proto.RelayMessage.newBuilder()
      .setOverlayId(protoOverlayId)
      .setPayload(ProtoByteString.copyFromUtf8("hello")))
    .build()
  private val joinMessage = JoinMessage(overlayId)
  private val protobufJoinMessage = proto.Message.newBuilder()
    .setJoinMessage(proto.JoinMessage.newBuilder().setOverlayId(protoOverlayId))
    .build()


  "Protobuf conversion" should "fail if the representation is not a message" in {
    shouldThrowConversionExceptionWithMessage("Cannot deserialize protobuf") {
      ProtobufConversion.fromProtobuf(ByteString("random data"))
    }
  }

  it should "convert a valid protobuf relay message" in {
    ProtobufConversion.fromProtobuf(protobufRelayMessage) shouldBe relayMessage
  }

  it should "reject overlay ids with other than 20 bytes" in {
    val invalidRelayMessage = protobufRelayMessage.toBuilder
      .setRelayMessage(protobufRelayMessage.getRelayMessage
        .toBuilder
        .setOverlayId(ProtoByteString.copyFromUtf8("123456789012345678901")))
      .build()
    shouldThrowConversionExceptionWithMessage("Invalid overlay id size: 21") {
      ProtobufConversion.fromProtobuf(invalidRelayMessage)
    }
  }

  it should "convert a valid protobuf join message" in {
    ProtobufConversion.fromProtobuf(protobufJoinMessage) shouldBe joinMessage
  }

  it should "convert a valid protobuf status message" in {
    ProtobufConversion.fromProtobuf(protobufStatusMessage(15)) shouldBe StatusMessage(15)
  }

  it should "reject status messages with invalid network sizes" in {
    shouldThrowConversionExceptionWithMessage("Invalid network size: -1") {
      ProtobufConversion.fromProtobuf(protobufStatusMessage(-1))
    }
  }

  it should "reject empty messages" in {
    val emptyProtobufMessage = proto.Message.newBuilder().build()
    shouldThrowConversionExceptionWithMessage("Empty message") {
      ProtobufConversion.fromProtobuf(emptyProtobufMessage)
    }
  }

  it should "reject multiple messages in one" in {
    val invalidProtobuf = protobufRelayMessage.toBuilder
      .setJoinMessage(protobufJoinMessage.getJoinMessage)
      .build()
    shouldThrowConversionExceptionWithMessage("Multiples messages in one") {
      ProtobufConversion.fromProtobuf(invalidProtobuf)
    }
  }

  it should "convert to protobuf a relay message" in {
    ProtobufConversion.toProtobuf(relayMessage) shouldBe protobufRelayMessage
  }

  it should "convert to protobuf a join message" in {
    ProtobufConversion.toProtobuf(joinMessage) shouldBe protobufJoinMessage
  }

  it should "convert to protobuf a status message" in {
    ProtobufConversion.toProtobuf(StatusMessage(8)) shouldBe protobufStatusMessage(8)
  }

  it should behave like havingRoundtripConversionBetween(
    relayMessage -> protobufRelayMessage,
    joinMessage -> protobufJoinMessage,
    StatusMessage(1) -> protobufStatusMessage(1)
  )

  private def shouldThrowConversionExceptionWithMessage(message: String)(block: => Unit): Unit = {
    val ex = the [ProtobufConversionException] thrownBy block
    ex.message should include(message)
  }

  private def protobufStatusMessage(networkSize: Int) = {
    val protobufStatusMessage = proto.Message.newBuilder()
      .setStatusMessage(proto.StatusMessage.newBuilder().setNetworkSize(networkSize))
      .build()
    protobufStatusMessage
  }

  private def havingRoundtripConversionBetween(mappings: (Message, proto.Message)*): Unit = {
    for ((message, protobufMessage) <- mappings) {
      it should s"have roundtrip serialization for $message" in {
        ProtobufConversion.toProtobuf(message) shouldBe protobufMessage
        ProtobufConversion.fromProtobuf(protobufMessage) shouldBe message
      }
    }
  }
}
