package coinffeine.protocol.serialization

import com.google.protobuf.Descriptors.FieldDescriptor

import coinffeine.model.currency.FiatCurrency
import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.arbitration.{CommitmentNotificationAck, CommitmentNotification}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.Payload._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.ProtocolVersion
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

private[serialization] class DefaultProtocolSerialization(
    transactionSerialization: TransactionSerialization) extends ProtocolSerialization {

  private val protoVersion = proto.ProtocolVersion.newBuilder()
    .setMajor(Version.Current.major)
    .setMinor(Version.Current.minor)
    .build()
  private val mappings = new DefaultProtoMappings(transactionSerialization)
  import mappings._

  override def toProtobuf(message: PublicMessage): proto.CoinffeineMessage =
    proto.CoinffeineMessage.newBuilder()
      .setVersion(protoVersion)
      .setPayload(toPayload(message)).build()

  private def toPayload(message: PublicMessage): proto.Payload.Builder = {
    val builder = proto.Payload.newBuilder
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

  override def fromProtobuf(message: proto.CoinffeineMessage): PublicMessage = {
    requireSameVersion(message.getVersion)
    fromPayload(message.getPayload)
  }

  private def requireSameVersion(messageVersion: ProtocolVersion): Unit = {
    val parsedVersion = Version(messageVersion.getMajor, messageVersion.getMinor)
    require(Version.Current == parsedVersion,
      s"Cannot deserialize message with version $parsedVersion, expected version ${Version.Current}")
  }

  private def fromPayload(payload: proto.Payload): PublicMessage = {
    val messageFields = payload.getAllFields
    val fieldNumber: Int = messageFields.size()
    require(fieldNumber >= 1, "Message has no content")
    require(fieldNumber <= 1, s"Malformed message with $fieldNumber fields: $payload")
    val descriptor: FieldDescriptor = messageFields.keySet().iterator().next()
    descriptor.getNumber match {
      case EXCHANGEABORTED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeAborted)
      case EXCHANGECOMMITMENT_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeCommitment)
      case COMMITMENTNOTIFICATION_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getCommitmentNotification)
      case COMMITMENTNOTIFICATIONACK_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getCommitmentNotificationAck)
      case ORDERMATCH_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOrderMatch)
      case QUOTEREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getQuoteRequest)
      case QUOTE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getQuote)
      case EXCHANGEREJECTION_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getExchangeRejection)
      case PEERHANDSHAKE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerHandshake)
      case REFUNDSIGNATUREREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getRefundSignatureRequest)
      case REFUNDSIGNATURERESPONSE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getRefundSignatureResponse)
      case STEPSIGNATURE_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getStepSignature)
      case PAYMENTPROOF_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPaymentProof)
      case MICROPAYMENTCHANNELCLOSED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getMicropaymentChannelClosed)
      case OPENORDERREQUEST_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOpenOrderRequest)
      case OPENORDERS_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getOpenOrders)
      case PEERPOSITIONS_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerPositions)
      case PEERPOSITIONSRECEIVED_FIELD_NUMBER =>
        ProtoMapping.fromProtobuf(payload.getPeerPositionsReceived)
      case _ => throw new IllegalArgumentException("Unsupported message: " + descriptor.getFullName)
    }
  }
}
