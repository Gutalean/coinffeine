package com.coinffeine.common.protocol.serialization

import java.math.BigInteger.ZERO
import scala.collection.JavaConversions

import org.reflections.Reflections

import com.coinffeine.common._
import com.coinffeine.common.Currency.UsDollar
import com.coinffeine.common.Currency.Implicits._
import com.coinffeine.common.bitcoin._
import com.coinffeine.common.bitcoin.Implicits._
import com.coinffeine.common.exchange.{Both, Exchange, PeerId}
import com.coinffeine.common.exchange.MicroPaymentChannel.Signatures
import com.coinffeine.common.network.CoinffeineUnitTestNetwork
import com.coinffeine.common.protocol.Version
import com.coinffeine.common.protocol.messages.PublicMessage
import com.coinffeine.common.protocol.messages.arbitration.CommitmentNotification
import com.coinffeine.common.protocol.messages.brokerage._
import com.coinffeine.common.protocol.messages.exchange.{PaymentProof, StepSignatures}
import com.coinffeine.common.protocol.messages.handshake._
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => proto}
import com.coinffeine.common.protocol.protobuf.CoinffeineProtobuf.CoinffeineMessage
import com.coinffeine.common.test.UnitTest

class DefaultProtocolSerializationTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

  val peerId = PeerId("peer")
  val exchangeId = Exchange.Id.random()
  val transactionSignature = new TransactionSignature(ZERO, ZERO)
  val sampleTxId = new Hash("d03f71f44d97243a83804b227cee881280556e9e73e5110ecdcb1bbf72d75c71")
  val btcAmount = 1.BTC
  val fiatAmount = 1.EUR

  val version = Version(major = 1, minor = 0)
  val protoVersion = proto.ProtocolVersion.newBuilder().setMajor(1).setMinor(0).build()
  val instance = new DefaultProtocolSerialization(version, new TransactionSerialization(network))


  "The default protocol serialization" should
    "support roundtrip serialization for all public messages" in new SampleMessages {
    sampleMessages.foreach { originalMessage =>
      val protoMessage = instance.toProtobuf(originalMessage, peerId)
      val (roundtripMessage, roundtripId) = instance.fromProtobuf(protoMessage)
      roundtripMessage should be (originalMessage)
      roundtripId should be (peerId)
    }
  }

  it must "throw when deserializing messages of a different protocol version" in {
    val message = CoinffeineMessage.newBuilder
      .setVersion(protoVersion.toBuilder.setMajor(42))
      .setPeerId("peerId")
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
      instance.toProtobuf(new PublicMessage {}, peerId)
    }
    ex.getMessage should include ("Unsupported message")
  }

  it must "throw when deserializing an empty protobuf message" in {
    val emptyMessage = CoinffeineMessage.newBuilder
      .setVersion(protoVersion)
      .setPeerId("peerId")
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
      .setPeerId("peerId")
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
      val orderSet: OrderSet[UsDollar.type] = OrderSet(
        market,
        bids = VolumeByPrice(100.USD -> 1.3.BTC),
        asks = VolumeByPrice(200.USD -> 0.3.BTC,
          250.USD -> 0.4.BTC)
      )
      Seq(
        ExchangeAborted(exchangeId, "reason"),
        ExchangeCommitment(exchangeId, transaction),
        CommitmentNotification(exchangeId, Both(sampleTxId, sampleTxId)),
        OrderMatch(exchangeId, btcAmount, fiatAmount, Both.fill(peerId)),
        orderSet,
        QuoteRequest(market),
        Quote(fiatAmount -> fiatAmount, fiatAmount),
        ExchangeRejection(exchangeId, "reason"),
        PeerHandshake(exchangeId, new KeyPair().publicKey, "paymentAccount"),
        RefundSignatureRequest(exchangeId, transaction),
        RefundSignatureResponse(exchangeId, transactionSignature),
        StepSignatures(exchangeId, 1, Signatures(transactionSignature, transactionSignature)),
        PaymentProof(exchangeId, "paymentId"),
        OpenOrdersRequest(market),
        OpenOrders(orderSet),
        PeerOrderRequests(market, Seq(
          Order(OrderId.random(), Bid, 1.BTC, 400.USD),
          Order(OrderId.random(), Ask, 0.4.BTC, 600.USD)
        ))
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
