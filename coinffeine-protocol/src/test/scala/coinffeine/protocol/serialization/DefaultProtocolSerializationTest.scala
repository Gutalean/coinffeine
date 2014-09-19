package coinffeine.protocol.serialization

import java.math.BigInteger.ZERO
import scala.collection.JavaConversions

import org.reflections.Reflections

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency.Currency.UsDollar
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.{Both, ExchangeId}
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.protocol.Version
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.arbitration.{CommitmentNotificationAck, CommitmentNotification}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange.{MicropaymentChannelClosed, PaymentProof, StepSignatures}
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => proto}

class DefaultProtocolSerializationTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

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
  val instance = new DefaultProtocolSerialization(new TransactionSerialization(network))


  "The default protocol serialization" should
    "support roundtrip serialization for all public messages" in new SampleMessages {
    sampleMessages.foreach { originalMessage =>
      val protoMessage = instance.toProtobuf(originalMessage)
      val roundtripMessage = instance.fromProtobuf(protoMessage)
      roundtripMessage should be (originalMessage)
    }
  }

  it must "throw when deserializing messages of a different protocol version" in {
    val message = CoinffeineMessage.newBuilder
      .setVersion(protoVersion.toBuilder.setMajor(42).setMinor(0))
      .setPayload(proto.Payload.newBuilder.setExchangeAborted(
        proto.ExchangeAborted.newBuilder.setExchangeId("id").setReason("reason")))
      .build
    val ex = the [IllegalArgumentException] thrownBy {
      instance.fromProtobuf(message)
    }
    ex.getMessage should include ("Cannot deserialize message with version 42.0")
  }

  it must "throw when serializing unknown public messages" in {
    val ex = the [IllegalArgumentException] thrownBy {
      instance.toProtobuf(new PublicMessage {})
    }
    ex.getMessage should include ("Unsupported message")
  }

  it must "throw when deserializing an empty protobuf message" in {
    val emptyMessage = CoinffeineMessage.newBuilder
      .setVersion(protoVersion)
      .setPayload(proto.Payload.newBuilder())
      .build
    val ex = the [IllegalArgumentException] thrownBy {
      instance.fromProtobuf(emptyMessage)
    }
    ex.getMessage should include ("Message has no content")
  }

  it must "throw when deserializing a protobuf message with multiple messages" in {
    val multiMessage = CoinffeineMessage.newBuilder
      .setVersion(protoVersion)
      .setPayload(proto.Payload.newBuilder
        .setExchangeAborted(proto.ExchangeAborted.newBuilder.setExchangeId("id").setReason("reason"))
        .setQuoteRequest(proto.QuoteRequest.newBuilder
          .setMarket(proto.Market.newBuilder().setCurrency("USD"))))
      .build
    val ex = the [IllegalArgumentException] thrownBy {
      instance.fromProtobuf(multiMessage)
    }
    ex.getMessage should include ("Malformed message with 2 fields")
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
        OrderBookEntry(Bid, 1.BTC, Price(400.USD)),
        OrderBookEntry(Ask, 0.4.BTC, Price(600.USD))
      ))
      val publicKey = new KeyPair().publicKey
      Seq(
        ExchangeAborted(exchangeId, "reason"),
        ExchangeCommitment(exchangeId, publicKey, transaction),
        CommitmentNotification(exchangeId, Both(sampleTxId, sampleTxId)),
        CommitmentNotificationAck(exchangeId),
        OrderMatch(orderId, exchangeId, Both.fill(btcAmount), Both.fill(fiatAmount), 310000L,
          PeerId("peer")),
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
