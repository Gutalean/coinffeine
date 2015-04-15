package coinffeine.protocol.serialization.protobuf

import java.math.BigInteger.ZERO
import scala.collection.JavaConversions
import scalaz.{Failure, Success}

import akka.util.ByteString
import org.reflections.Reflections
import org.scalatest.Inside
import org.scalautils.TypeCheckedTripleEquals

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange.{MicropaymentChannelClosed, PaymentProof, StepSignatures}
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage.MessageType
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}
import coinffeine.protocol.serialization.ProtocolSerialization._
import coinffeine.protocol.serialization.{Payload, ProtocolMismatch, TransactionSerialization}

class ProtobufProtocolSerializationTest extends UnitTest with TypeCheckedTripleEquals with Inside
  with CoinffeineUnitTestNetwork.Component {

  val orderId = OrderId.random()
  val exchangeId = ExchangeId.random()
  val transactionSignature = new TransactionSignature(ZERO, ZERO)
  val sampleTxId = new Hash("d03f71f44d97243a83804b227cee881280556e9e73e5110ecdcb1bbf72d75c71")
  val btcAmount = 1.BTC
  val fiatAmount = 1.EUR

  val protoVersion = proto.ProtocolVersion.newBuilder
    .setMajor(Version.Current.major)
    .setMinor(Version.Current.minor)
    .build()
  val instance = new ProtobufProtocolSerialization(new TransactionSerialization(network))

  "The default protocol serialization" should
    "support roundtrip serialization for all public messages" in new SampleMessages {
    sampleMessages.foreach { originalMessage =>
      val protoMessage = instance.toProtobuf(Payload(originalMessage))
      val roundtripMessage = instance.fromProtobuf(protoMessage.toOption.get)
      roundtripMessage should === (Success(Payload(originalMessage)))
    }
  }

  it must "support roundtrip serialization of protocol mismatch messages" in {
    val mismatch = ProtocolMismatch(Version(42, 0))
    val protobufMismatch = proto.CoinffeineMessage.newBuilder()
      .setType(MessageType.PROTOCOL_MISMATCH)
      .setProtocolMismatch(proto.ProtocolMismatch.newBuilder()
      .setSupportedVersion(protoVersion.toBuilder.setMajor(42).setMinor(0)))
      .build()
    instance.toProtobuf(mismatch) should === (Success(protobufMismatch))
    instance.fromProtobuf(protobufMismatch) should === (Success(mismatch))
  }

  it must "detect messages that are invalid protobuf messages" in {
    inside(instance.deserialize(ByteString("totally invalid"))) {
      case Failure(InvalidProtocolBuffer(_)) =>
    }
  }

  it must "detect messages of a different protocol version" in {
    val message = proto.CoinffeineMessage.newBuilder
      .setType(MessageType.PAYLOAD)
      .setPayload(
        proto.Payload.newBuilder
          .setVersion(protoVersion.toBuilder.setMajor(42).setMinor(0))
          .setExchangeAborted(
            proto.ExchangeAborted.newBuilder.setExchangeId("id").setReason("reason"))
      ).build
    instance.fromProtobuf(message) should === (Failure(IncompatibleVersion(
      actual = Version(42, 0),
      expected = Version.Current
    )))
  }

  it must "reject serializing unknown public messages" in {
    val unsupportedMessage = new PublicMessage {}
    instance.serialize(Payload(unsupportedMessage)) should === (
      Failure(UnsupportedMessageClass(unsupportedMessage.getClass)))
  }

  it must "detect empty protobuf payloads" in {
    val emptyMessage = proto.CoinffeineMessage.newBuilder
      .setType(MessageType.PAYLOAD)
      .setPayload(proto.Payload.newBuilder().setVersion(protoVersion))
      .build
    instance.fromProtobuf(emptyMessage) should === (Failure(EmptyPayload))
  }

  it must "detect a protobuf message with multiple payloads" in {
    val multiMessage = proto.CoinffeineMessage.newBuilder
      .setType(MessageType.PAYLOAD)
      .setPayload(proto.Payload.newBuilder
        .setVersion(protoVersion)
        .setExchangeAborted(
          proto.ExchangeAborted.newBuilder.setExchangeId("id").setReason("reason"))
        .setQuoteRequest(proto.QuoteRequest.newBuilder
          .setMarket(proto.Market.newBuilder().setCurrency("USD"))))
      .build
    instance.fromProtobuf(multiMessage) should === (
      Failure(MultiplePayloads(Set("exchangeAborted", "quoteRequest"))))
  }

  it must "detect inconsistent message types" in {
    val missingPayloadMessage = proto.CoinffeineMessage.newBuilder
      .setType(MessageType.PAYLOAD)
      .build()
    instance.fromProtobuf(missingPayloadMessage) should === (Failure(MissingField("payload")))
    val missingMismatchMessage = proto.CoinffeineMessage.newBuilder
      .setType(MessageType.PROTOCOL_MISMATCH)
      .build()
    instance.fromProtobuf(missingMismatchMessage) should === (
      Failure(MissingField("protocolMismatch")))
  }

  trait SampleMessages {
    val transaction = {
      val tx = new MutableTransaction(network)
      tx.setLockTime(42)
      ImmutableTransaction(tx)
    }
    val sampleMessages = {
      val market = Market(UsDollar)
      val peerPositions = PeerPositions(market, Seq(
        OrderBookEntry.random(Bid, 1.BTC, Price(400.USD)),
        OrderBookEntry.random(Ask, 0.4.BTC, Price(600.USD))
      ))
      val publicKey = new KeyPair().publicKey
      Seq(
        ExchangeAborted(exchangeId, "reason"),
        ExchangeCommitment(exchangeId, publicKey, transaction),
        CommitmentNotification(exchangeId, Both(sampleTxId, sampleTxId)),
        CommitmentNotificationAck(exchangeId),
        OrderMatch(orderId, exchangeId, Both.fill(btcAmount), Both.fill(fiatAmount), 310000L,
          PeerId.hashOf("peer")),
        QuoteRequest(market),
        Quote(fiatAmount -> fiatAmount, fiatAmount),
        ExchangeRejection(exchangeId, "reason"),
        PeerHandshake(exchangeId, publicKey, "paymentAccount"),
        RefundSignatureRequest(exchangeId, transaction),
        RefundSignatureResponse(exchangeId, transactionSignature),
        StepSignatures(exchangeId, 1, Both(transactionSignature, transactionSignature)),
        PaymentProof(exchangeId, "paymentId", 5),
        MicropaymentChannelClosed(exchangeId),
        OpenOrdersRequest(market),
        OpenOrders(peerPositions),
        peerPositions,
        PeerPositionsReceived("nonce-1234567890")
      )
    }

    requireSampleInstancesForAllPublicMessages(sampleMessages)
  }

  /** Make sure we have a working serialization for all defined public messages. */
  private def requireSampleInstancesForAllPublicMessages(messages: Seq[PublicMessage]): Unit = {
    scanPublicMessagesFromClasspath().foreach { messageClass =>
      require(
        messages.exists(_.getClass == messageClass),
        s"There is not a sample instance of ${messageClass.getCanonicalName}"
      )
    }
  }

  private def scanPublicMessagesFromClasspath(): Set[Class[_ <: PublicMessage]] = {
    val publicMessage = classOf[PublicMessage]
    val basePackage = publicMessage.getPackage.getName
    val reflections = new Reflections(basePackage)
    JavaConversions.asScalaSet(reflections.getSubTypesOf(publicMessage)).toSet
  }
}
