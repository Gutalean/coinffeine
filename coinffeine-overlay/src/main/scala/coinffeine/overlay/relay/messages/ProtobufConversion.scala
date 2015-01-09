package coinffeine.overlay.relay.messages

import akka.util.ByteString
import com.google.protobuf.{ByteString => ProtoByteString, InvalidProtocolBufferException}

import coinffeine.overlay.OverlayId
import coinffeine.overlay.relay.protobuf.{RelayProtobuf => proto}

/** Offers conversion of relay protocol messages from/to protocol buffers */
private[relay] object ProtobufConversion {
  private val OverlayIdBytes = 20

  case class ProtobufConversionException(message: String, cause: Throwable = null)
    extends Exception(message, cause)

  @throws[ProtobufConversionException]
  def fromProtobuf(bytes: ByteString): Message = {
    val rawMessage = try {
      proto.Message.parseFrom(bytes.toArray)
    } catch {
      case ex: InvalidProtocolBufferException =>
        throw ProtobufConversionException("Cannot deserialize protobuf", ex)
    }
    fromProtobuf(rawMessage)
  }

  @throws[ProtobufConversionException]
  def fromProtobuf(protobuf: proto.Message): Message = {
    requireAtMostOneMessage(protobuf)
    if (protobuf.hasJoinMessage) fromProtobuf(protobuf.getJoinMessage)
    else if (protobuf.hasRelayMessage) fromProtobuf(protobuf.getRelayMessage)
    else if (protobuf.hasStatusMessage) fromProtobuf(protobuf.getStatusMessage)
    else throw new ProtobufConversionException("Empty message")
  }

  private def requireAtMostOneMessage(protobuf: proto.Message): Unit = {
    val messageCount = Seq(
      protobuf.hasJoinMessage,
      protobuf.hasRelayMessage,
      protobuf.hasStatusMessage
    ).count(identity)
    require(messageCount <= 1, s"Multiples messages in one: $protobuf")
  }

  private def fromProtobuf(protobuf: proto.JoinMessage) =
    JoinMessage(overlayIdFromProtobuf(protobuf.getOverlayId))

  private def fromProtobuf(protobuf: proto.RelayMessage) = RelayMessage(
    id = overlayIdFromProtobuf(protobuf.getOverlayId),
    payload = ByteString(protobuf.getPayload.toByteArray)
  )

  private def fromProtobuf(protobuf: proto.StatusMessage) = {
    require(protobuf.getNetworkSize >= 0, s"Invalid network size: ${protobuf.getNetworkSize}")
    StatusMessage(protobuf.getNetworkSize)
  }

  private def overlayIdFromProtobuf(protobuf: ProtoByteString) = {
    require(protobuf.size() == OverlayIdBytes, s"Invalid overlay id size: ${protobuf.size()}")
    OverlayId(BigInt((0: Byte) +: protobuf.toByteArray))
  }

  private def require(predicate: Boolean, message: => String): Unit = {
    if (!predicate) {
      throw ProtobufConversionException(message)
    }
  }

  def toByteString(message: Message): ByteString = ByteString(toProtobuf(message).toByteArray)

  def toProtobuf(message: Message): proto.Message = {
    val messageWrapper = proto.Message.newBuilder()
    message match {
      case RelayMessage(id, payload) =>
        messageWrapper.setRelayMessage(proto.RelayMessage.newBuilder()
          .setOverlayId(toProtobuf(id))
          .setPayload(toProtobuf(payload)))
      case JoinMessage(id) =>
        messageWrapper.setJoinMessage(proto.JoinMessage.newBuilder().setOverlayId(toProtobuf(id)))
      case StatusMessage(networkSize) =>
        messageWrapper.setStatusMessage(proto.StatusMessage.newBuilder().setNetworkSize(networkSize))
    }
    messageWrapper.build()
  }

  private def toProtobuf(overlayId: OverlayId): ProtoByteString = {
    val bytes = overlayId.value.toByteArray.takeRight(OverlayIdBytes)
    val padding = Array.fill[Byte](OverlayIdBytes - bytes.size)(0)
    ProtoByteString.copyFrom(padding ++ bytes)
  }

  private def toProtobuf(payload: ByteString): ProtoByteString =
    ProtoByteString.copyFrom(payload.toArray)
}
