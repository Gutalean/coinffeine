package coinffeine.protocol.serialization

import scala.collection.JavaConverters._
import scalaz.Validation
import scalaz.syntax.validation._
import scalaz.Validation.FlatMap._

import com.google.protobuf.Descriptors.FieldDescriptor

import coinffeine.model.currency.FiatCurrency
import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.arbitration.{CommitmentNotificationAck, CommitmentNotification}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage.MessageType
import coinffeine.protocol.protobuf.CoinffeineProtobuf.Payload._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.ProtocolVersion
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.ProtocolSerialization._

private[serialization] class DefaultProtocolSerialization(
    transactionSerialization: TransactionSerialization) extends ProtocolSerialization {

  private val protoVersion = toProtobuf(Version.Current)
  private val mappings = new DefaultProtoMappings(transactionSerialization)
  import mappings._

  override def toProtobuf(message: CoinffeineMessage): proto.CoinffeineMessage = {
    val builder = proto.CoinffeineMessage.newBuilder()
    message match {
      case Payload(payload) =>
          builder.setType(MessageType.PAYLOAD).setPayload(toPayload(payload))
      case ProtocolMismatch(supportedVersion) =>
        builder.setType(MessageType.PROTOCOL_MISMATCH).setProtocolMismatch(
          proto.ProtocolMismatch.newBuilder().setSupportedVersion(toProtobuf(supportedVersion)))
    }
    builder.build()
  }

  private def toProtobuf(version: Version): proto.ProtocolVersion =
    proto.ProtocolVersion.newBuilder()
      .setMajor(version.major)
      .setMinor(version.minor)
      .build()

  private def fromProtobuf(version: proto.ProtocolVersion): Version =
    Version(version.getMajor, version.getMinor)

  private def toPayload(message: PublicMessage): proto.Payload.Builder = {
    val builder = proto.Payload.newBuilder
    builder.setVersion(protoVersion)
    message match {
      case m: ExchangeAborted =>
        builder.setExchangeAborted(ProtoMapping.toProtobuf(m))
      case m: ExchangeCommitment =>
        builder.setExchangeCommitment(ProtoMapping.toProtobuf(m))
      case m: CommitmentNotification =>
        builder.setCommitmentNotification(ProtoMapping.toProtobuf(m))
      case m: CommitmentNotificationAck =>
        builder.setCommitmentNotificationAck(ProtoMapping.toProtobuf(m))
      case m @ OrderMatch(_, _, _, _, _, _) =>
        builder.setOrderMatch(orderMatchMapping.toProtobuf(m))
      case m: QuoteRequest =>
        builder.setQuoteRequest(ProtoMapping.toProtobuf(m))
      case m @ Quote(_, _, _) =>
        builder.setQuote(ProtoMapping.toProtobuf[Quote[_ <: FiatCurrency], proto.Quote](m))
      case m: ExchangeRejection =>
        builder.setExchangeRejection(ProtoMapping.toProtobuf(m))
      case m: PeerHandshake =>
        builder.setPeerHandshake(ProtoMapping.toProtobuf(m))
      case m: RefundSignatureRequest =>
        builder.setRefundSignatureRequest(ProtoMapping.toProtobuf(m))
      case m: RefundSignatureResponse =>
        builder.setRefundSignatureResponse(ProtoMapping.toProtobuf(m))
      case m: StepSignatures =>
        builder.setStepSignature(ProtoMapping.toProtobuf(m))
      case m: PaymentProof =>
        builder.setPaymentProof(ProtoMapping.toProtobuf(m))
      case m: MicropaymentChannelClosed =>
        builder.setMicropaymentChannelClosed(ProtoMapping.toProtobuf(m))
      case m: OpenOrdersRequest =>
        builder.setOpenOrderRequest(ProtoMapping.toProtobuf(m))
      case m @ OpenOrders(_) =>
        builder.setOpenOrders(
          ProtoMapping.toProtobuf[OpenOrders[_ <: FiatCurrency], proto.OpenOrders](m))
      case m @ PeerPositions(_, _, _) =>
        builder.setPeerPositions(
          ProtoMapping.toProtobuf[PeerPositions[_ <: FiatCurrency], proto.PeerPositions](m))
      case m: PeerPositionsReceived =>
        builder.setPeerPositionsReceived(ProtoMapping.toProtobuf(m))
      case _ => throw new IllegalArgumentException("Unsupported message: " + message)
    }
    builder
  }

  override def fromProtobuf(message: proto.CoinffeineMessage): Deserialization = {
    message.getType match {
      case MessageType.PAYLOAD =>
        if (message.hasPayload) fromPayload(message.getPayload)
        else MissingField("payload").failure

      case MessageType.PROTOCOL_MISMATCH =>
        if (message.hasProtocolMismatch)
          ProtocolMismatch(fromProtobuf(message.getProtocolMismatch.getSupportedVersion)).success
        else MissingField("protocolMismatch").failure
    }
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
