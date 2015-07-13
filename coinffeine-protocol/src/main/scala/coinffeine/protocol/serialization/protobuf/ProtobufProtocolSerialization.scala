package coinffeine.protocol.serialization.protobuf

import scala.collection.JavaConverters._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.syntax.validation._

import akka.util.ByteString
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.InvalidProtocolBufferException

import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage.MessageType
import coinffeine.protocol.protobuf.CoinffeineProtobuf.Payload._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.ProtocolVersion
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.ProtocolSerialization._
import coinffeine.protocol.serialization._

class ProtobufProtocolSerialization(transactionSerialization: TransactionSerialization)
  extends ProtocolSerialization {

  private val protoVersion = toProtobuf(Version.Current)
  private val mappings = new ProtoMappings(transactionSerialization)
  import mappings._

  override def serialize(message: CoinffeineMessage): Serialization =
    toProtobuf(message).map(m => ByteString(m.toByteArray))

  def toProtobuf(message: CoinffeineMessage): Validation[SerializationError, proto.CoinffeineMessage] = {
    message match {
      case Payload(payload) =>
        for {
          protoPayload<- toPayload(payload)
        } yield {
          proto.CoinffeineMessage.newBuilder().
            setType(MessageType.PAYLOAD)
            .setPayload(protoPayload)
            .build()
        }

      case ProtocolMismatch(supportedVersion) =>
        proto.CoinffeineMessage.newBuilder()
          .setType(MessageType.PROTOCOL_MISMATCH)
          .setProtocolMismatch(
            proto.ProtocolMismatch.newBuilder().setSupportedVersion(toProtobuf(supportedVersion)))
          .build()
          .success
    }
  }

  private def toProtobuf(version: Version): proto.ProtocolVersion =
    proto.ProtocolVersion.newBuilder()
      .setMajor(version.major)
      .setMinor(version.minor)
      .build()

  private def fromProtobuf(version: proto.ProtocolVersion): Version =
    Version(version.getMajor, version.getMinor)

  private def toPayload(message: PublicMessage): Validation[SerializationError, proto.Payload.Builder] = {
    val builder = proto.Payload.newBuilder
    builder.setVersion(protoVersion)
    message match {
      case m: ExchangeAborted =>
        builder.setExchangeAborted(ProtoMapping.toProtobuf(m)).success
      case m: ExchangeCommitment =>
        builder.setExchangeCommitment(ProtoMapping.toProtobuf(m)).success
      case m: CommitmentNotification =>
        builder.setCommitmentNotification(ProtoMapping.toProtobuf(m)).success
      case m: CommitmentNotificationAck =>
        builder.setCommitmentNotificationAck(ProtoMapping.toProtobuf(m)).success
      case m @ OrderMatch(_, _, _, _, _, _) =>
        builder.setOrderMatch(orderMatchMapping.toProtobuf(m)).success
      case m: QuoteRequest =>
        builder.setQuoteRequest(ProtoMapping.toProtobuf(m)).success
      case m @ Quote(_, _, _) =>
        builder.setQuote(ProtoMapping.toProtobuf[Quote, proto.Quote](m)).success
      case m: ExchangeRejection =>
        builder.setExchangeRejection(ProtoMapping.toProtobuf(m)).success
      case m: PeerHandshake =>
        builder.setPeerHandshake(ProtoMapping.toProtobuf(m)).success
      case m: RefundSignatureRequest =>
        builder.setRefundSignatureRequest(ProtoMapping.toProtobuf(m)).success
      case m: RefundSignatureResponse =>
        builder.setRefundSignatureResponse(ProtoMapping.toProtobuf(m)).success
      case m: StepSignatures =>
        builder.setStepSignature(ProtoMapping.toProtobuf(m)).success
      case m: PaymentProof =>
        builder.setPaymentProof(ProtoMapping.toProtobuf(m)).success
      case m: MicropaymentChannelClosed =>
        builder.setMicropaymentChannelClosed(ProtoMapping.toProtobuf(m)).success
      case m: OpenOrdersRequest =>
        builder.setOpenOrderRequest(ProtoMapping.toProtobuf(m)).success
      case m @ OpenOrders(_) =>
        builder.setOpenOrders(
          ProtoMapping.toProtobuf[OpenOrders, proto.OpenOrders](m)).success
      case m @ PeerPositions(_, _, _) =>
        builder.setPeerPositions(
          ProtoMapping.toProtobuf[PeerPositions, proto.PeerPositions](m)).success
      case m: PeerPositionsReceived =>
        builder.setPeerPositionsReceived(ProtoMapping.toProtobuf(m)).success
      case _ => UnsupportedMessageClass(message.getClass).failure
    }
  }

  override def deserialize(bytes: ByteString): Deserialization = {
    (try {
      proto.CoinffeineMessage.parseFrom(bytes.toArray).success
    } catch {
      case ex: InvalidProtocolBufferException =>
        InvalidProtocolBuffer(ex.getMessage).failure
    }).flatMap(fromProtobuf)
  }

  def fromProtobuf(message: proto.CoinffeineMessage): Deserialization = {
    message.getType match {
      case MessageType.PAYLOAD => fromPayload(message)

      case MessageType.PROTOCOL_MISMATCH =>
        if (message.hasProtocolMismatch)
          ProtocolMismatch(fromProtobuf(message.getProtocolMismatch.getSupportedVersion)).success
        else MissingField("protocolMismatch").failure
    }
  }

  private def fromPayload(message: proto.CoinffeineMessage): Deserialization =
    if (!message.hasPayload) MissingField("payload").failure
    else try fromPayload(message.getPayload)
    catch {
      case ex: IllegalArgumentException => ConstraintViolation(ex.getMessage).failure
    }

  private def fromPayload(payload: proto.Payload): Deserialization = for {
    _ <- requireSameVersion(payload.getVersion)
    descriptor <- requireJustOneOptionalField(payload.getAllFields.keySet().asScala.toSet)
    payload <- descriptor.getNumber match {
      case EXCHANGEABORTED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeAborted).success
      case EXCHANGECOMMITMENT_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeCommitment).success
      case COMMITMENTNOTIFICATION_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getCommitmentNotification).success
      case COMMITMENTNOTIFICATIONACK_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getCommitmentNotificationAck).success
      case ORDERMATCH_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOrderMatch).success
      case QUOTEREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getQuoteRequest).success
      case QUOTE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getQuote).success
      case EXCHANGEREJECTION_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeRejection).success
      case PEERHANDSHAKE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerHandshake).success
      case REFUNDSIGNATUREREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getRefundSignatureRequest).success
      case REFUNDSIGNATURERESPONSE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getRefundSignatureResponse).success
      case STEPSIGNATURE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getStepSignature).success
      case PAYMENTPROOF_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPaymentProof).success
      case MICROPAYMENTCHANNELCLOSED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getMicropaymentChannelClosed).success
      case OPENORDERREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOpenOrderRequest).success
      case OPENORDERS_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOpenOrders).success
      case PEERPOSITIONS_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerPositions).success
      case PEERPOSITIONSRECEIVED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerPositionsReceived).success
      case _ => UnsupportedProtobufMessage(descriptor.getName).failure
    }
  } yield Payload(payload)

  private def requireSameVersion(
      messageVersion: ProtocolVersion): Validation[DeserializationError, Unit] = {
    val parsedVersion = fromProtobuf(messageVersion)
    if (Version.Current == parsedVersion) ().success
    else IncompatibleVersion(parsedVersion, Version.Current).failure
  }

  private def requireJustOneOptionalField(
      fields: Set[FieldDescriptor]): Validation[DeserializationError, FieldDescriptor] = {
    val optionalFields = fields.filter(_.isOptional)
    optionalFields.size match {
      case 0 => EmptyPayload.failure
      case 1 => optionalFields.head.success
      case _ => MultiplePayloads(optionalFields.map(_.getName)).failure
    }
  }
}
