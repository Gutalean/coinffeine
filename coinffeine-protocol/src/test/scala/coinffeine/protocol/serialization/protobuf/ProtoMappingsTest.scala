package coinffeine.protocol.serialization.protobuf

import java.math.BigInteger

import com.google.protobuf.{ByteString, Message}

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order.{OrderId, Bid, MarketPrice, Price}
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange.MicropaymentChannelClosed
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => msg}
import coinffeine.protocol.serialization.TransactionSerialization

class ProtoMappingsTest extends UnitTest with CoinffeineUnitTestNetwork.Component {

  private val commitmentTransaction = ImmutableTransaction(new MutableTransaction(network))
  private val txSerialization = new TransactionSerialization(network)
  private val testMappings = new ProtoMappings(txSerialization)
  private val publicKey = new KeyPair().publicKey
  import testMappings._

  final def thereIsAMappingBetween[T, M <: Message](obj: T, msg: M)
                                                   (implicit mapping: ProtoMapping[T, M]): Unit = {

    it should "convert the case class into the protobuf message" in {
      ProtoMapping.toProtobuf(obj) shouldBe msg
    }

    it should "convert to protobuf message to the case class" in {
      ProtoMapping.fromProtobuf(msg) shouldBe obj
    }

    it should "convert to protobuf and back again" in {
      ProtoMapping.fromProtobuf(ProtoMapping.toProtobuf(obj)) shouldBe obj
    }
  }

  val sampleTxId = new Hash("d03f71f44d97243a83804b227cee881280556e9e73e5110ecdcb1bbf72d75c71")
  val sampleOrderId = OrderId.random()
  val sampleOrderIdMessage = msg.OrderId.newBuilder().setValue(sampleOrderId.value).build()
  val sampleExchangeId = ExchangeId.random()
  val sampleExchangeIdMessage = msg.ExchangeId.newBuilder().setValue(sampleExchangeId.value).build()

  val bigDecimal = BigDecimal(1.1)
  val decimalNumber = msg.DecimalNumber.newBuilder
    .setScale(1)
    .setValue(ByteString.copyFrom(Array[Byte](11)))
    .build()
  "Decimal number" should behave like thereIsAMappingBetween(bigDecimal, decimalNumber)

  val extremelySmallDecimal = BigDecimal(10).pow(-40)
  val smallDecimalNumber = msg.DecimalNumber.newBuilder
    .setScale(40)
    .setValue(ByteString.copyFrom(Array[Byte](1)))
    .build()
  "Really small decimal number" should behave like
    thereIsAMappingBetween(extremelySmallDecimal, smallDecimalNumber)

  "Fiat amount" should behave like thereIsAMappingBetween[FiatAmount, msg.FiatAmount](
    3.EUR, msg.FiatAmount.newBuilder
      .setCurrency("EUR")
      .setAmount(decimalNumberMapping.toProtobuf(3))
      .build
    )

  val limitOrderBookEntry = OrderBookEntry(sampleOrderId, Bid, 10.BTC, Price(400.EUR))
  val limitOrderBookEntryMessage = msg.OrderBookEntry.newBuilder
    .setId(sampleOrderIdMessage)
    .setOrderType(msg.OrderBookEntry.OrderType.BID)
    .setAmount(decimalNumberMapping.toProtobuf(10))
    .setPrice(msg.Price.newBuilder
      .setCurrency("EUR")
      .setLimit(decimalNumberMapping.toProtobuf(400)))
    .build
  val marketOrderBookEntry = limitOrderBookEntry.copy(price = MarketPrice(Euro))
  val marketOrderBookEntryMessage = limitOrderBookEntryMessage.toBuilder
    .setPrice(msg.Price.newBuilder.setCurrency("EUR"))
    .build()

  "Limit-price order" should behave like
    thereIsAMappingBetween[OrderBookEntry, msg.OrderBookEntry](
      limitOrderBookEntry, limitOrderBookEntryMessage)

  "Market-price order" should behave like
    thereIsAMappingBetween[OrderBookEntry, msg.OrderBookEntry](
      marketOrderBookEntry, marketOrderBookEntryMessage)

  val positions = PeerPositions(Market(Euro), Seq(limitOrderBookEntry), "nonce-1234567890")
  val positionsMessage = msg.PeerPositions.newBuilder
    .setMarket(msg.Market.newBuilder.setCurrency("EUR"))
    .setNonce("nonce-1234567890")
    .addEntries(limitOrderBookEntryMessage)
    .build

  "Peer positions" should behave like
    thereIsAMappingBetween[PeerPositions, msg.PeerPositions](
      positions, positionsMessage)

  val positionsReceived = PeerPositionsReceived("nonce-1234567890")
  val positionsReceivedMessage = msg.PeerPositionsReceived.newBuilder
    .setNonce("nonce-1234567890")
    .build

  "Peer positions received" should behave like
    thereIsAMappingBetween[PeerPositionsReceived, msg.PeerPositionsReceived](
      positionsReceived, positionsReceivedMessage)

  val commitmentNotification = CommitmentNotification(sampleExchangeId, Both(sampleTxId, sampleTxId))
  val commitmentNotificationMessage = msg.CommitmentNotification.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setBuyerTxId(ByteString.copyFrom(sampleTxId.getBytes))
    .setSellerTxId(ByteString.copyFrom(sampleTxId.getBytes))
    .build()

  "Commitment notification" should behave like thereIsAMappingBetween(
    commitmentNotification, commitmentNotificationMessage)

  val commitmentNotificationAck = CommitmentNotificationAck(sampleExchangeId)
  val commitmentNotificationAckMessage = msg.CommitmentNotificationAck.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .build()

  "Commitment notification acknowledge" should behave like thereIsAMappingBetween(
    commitmentNotificationAck, commitmentNotificationAckMessage)

  val commitment = ExchangeCommitment(sampleExchangeId, publicKey, commitmentTransaction)
  val commitmentMessage = msg.ExchangeCommitment.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setPublicKey(txSerialization.serialize(publicKey))
    .setCommitmentTransaction( txSerialization.serialize(commitmentTransaction))
    .build()

  "Enter exchange" must behave like thereIsAMappingBetween(commitment, commitmentMessage)

  val exchangeAborted = ExchangeAborted(ExchangeId(sampleExchangeId.value),
    ExchangeAborted.Timeout)
  val exchangeAbortedMessage = msg.ExchangeAborted.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setCause(msg.ExchangeAborted.Cause.TIMEOUT)
    .build()

  "Exchange aborted" should behave like thereIsAMappingBetween(
    exchangeAborted, exchangeAbortedMessage)

  val exchangeRejection = ExchangeRejection(
    exchangeId = sampleExchangeId,
    cause = ExchangeRejection.InvalidOrderMatch)
  val exchangeRejectionMessage = msg.ExchangeRejection.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setCause(msg.ExchangeRejection.Cause.INVALID_ORDER_MATCH)
    .build()

  "Exchange rejection" should behave like thereIsAMappingBetween(
    exchangeRejection, exchangeRejectionMessage)

  val buyer = PeerId.hashOf("buyer")
  val orderMatch = OrderMatch(
    orderId = sampleOrderId,
    exchangeId = sampleExchangeId,
    bitcoinAmount = Both(buyer = 0.1.BTC, seller = 0.1003.BTC),
    fiatAmount = Both(buyer = 10050.EUR, seller = 10000.EUR),
    lockTime = 310000L,
    counterpart = buyer
  )
  val orderMatchMessage = msg.OrderMatch.newBuilder
    .setOrderId(sampleOrderIdMessage)
    .setExchangeId(sampleExchangeIdMessage)
    .setCurrency("EUR")
    .setBuyerBitcoinAmount(ProtoMapping.toProtobuf(BigDecimal(0.1)))
    .setSellerBitcoinAmount(ProtoMapping.toProtobuf(BigDecimal(0.1003)))
    .setBuyerFiatAmount(ProtoMapping.toProtobuf(BigDecimal(10050)))
    .setSellerFiatAmount(ProtoMapping.toProtobuf(BigDecimal(10000)))
    .setLockTime(310000L)
    .setCounterpart(msg.PeerId.newBuilder().setValue(buyer.value))
    .build
  "Order match" must behave like
    thereIsAMappingBetween[OrderMatch, msg.OrderMatch](
      orderMatch, orderMatchMessage)

  val emptyQuoteMessage = msg.Quote.newBuilder
    .setMarket(msg.Market.newBuilder.setCurrency("EUR"))
    .build
  val emptyQuote = Quote.empty(Market(Euro))
  "Empty quote" must behave like thereIsAMappingBetween[Quote, msg.Quote](
    emptyQuote, emptyQuoteMessage)

  val quoteMessage = emptyQuoteMessage.toBuilder
    .setHighestBid(decimalNumberMapping.toProtobuf(20))
    .setLowestAsk(decimalNumberMapping.toProtobuf(30))
    .setLastPrice(decimalNumberMapping.toProtobuf(22))
    .build
  val quote = Quote(20.EUR -> 30.EUR, 22 EUR)
  "Quote" must behave like thereIsAMappingBetween[Quote, msg.Quote](
    quote, quoteMessage)

  val quoteRequest = QuoteRequest(Market(Euro))
  val quoteRequestMessage = msg.QuoteRequest.newBuilder
    .setMarket(msg.Market.newBuilder.setCurrency("EUR"))
    .build

  "Quote request" must behave like thereIsAMappingBetween(quoteRequest, quoteRequestMessage)

  val peerHandshake = PeerHandshake(sampleExchangeId, publicKey, "accountId")
  val peerHandshakeMessage = msg.PeerHandshake.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setPublicKey(ByteString.copyFrom(publicKey.getPubKey))
    .setPaymentProcessorAccount("accountId")
    .build()

  "Peer handshake" must behave like thereIsAMappingBetween(peerHandshake, peerHandshakeMessage)

  val refundTx = ImmutableTransaction(new MutableTransaction(network))
  val refundSignatureRequest = RefundSignatureRequest(sampleExchangeId, refundTx)
  val refundSignatureRequestMessage = msg.RefundSignatureRequest.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setRefundTx(ByteString.copyFrom(refundTx.get.bitcoinSerialize()))
    .build()

  "Refund signature request" must behave like thereIsAMappingBetween(
    refundSignatureRequest, refundSignatureRequestMessage)

  val refundTxSignature = new TransactionSignature(BigInteger.ZERO, BigInteger.ZERO)
  val refundSignatureResponse = RefundSignatureResponse(sampleExchangeId, refundTxSignature)
  val refundSignatureResponseMessage = msg.RefundSignatureResponse.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .setTransactionSignature(ByteString.copyFrom(refundTxSignature.encodeToBitcoin()))
    .build()

  "Refund signature response" must behave like thereIsAMappingBetween(
    refundSignatureResponse, refundSignatureResponseMessage)

  val micropaymentChannelClosed = MicropaymentChannelClosed(sampleExchangeId)
  val micropaymentChannelClosedMessage = msg.MicropaymentChannelClosed.newBuilder()
    .setExchangeId(sampleExchangeIdMessage)
    .build()

  "Micropayment channel closed" must behave like thereIsAMappingBetween(
    micropaymentChannelClosed, micropaymentChannelClosedMessage)
}
