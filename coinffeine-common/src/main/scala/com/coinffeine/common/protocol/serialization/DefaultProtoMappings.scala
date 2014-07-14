package com.coinffeine.common.protocol.serialization

import java.math.BigDecimal
import java.util.Currency
import scala.collection.JavaConverters._

import com.google.protobuf.ByteString

import com.coinffeine.common._
import com.coinffeine.common.Currency.Bitcoin
import com.coinffeine.common.bitcoin.Hash
import com.coinffeine.common.exchange.{Both, Exchange, PeerId}
import com.coinffeine.common.exchange.MicroPaymentChannel.Signatures
import com.coinffeine.common.protocol.messages.arbitration.CommitmentNotification
import com.coinffeine.common.protocol.messages.brokerage._
import com.coinffeine.common.protocol.messages.exchange._
import com.coinffeine.common.protocol.messages.handshake._
import com.coinffeine.common.protocol.protobuf.{CoinffeineProtobuf => msg}

/** Implicit conversion mappings for the protocol messages */
private[serialization] class DefaultProtoMappings(txSerialization: TransactionSerialization) {

  implicit val commitmentNotificationMapping =
    new ProtoMapping[CommitmentNotification, msg.CommitmentNotification] {

      override def fromProtobuf(commitment: msg.CommitmentNotification) = CommitmentNotification(
        exchangeId = Exchange.Id(commitment.getExchangeId),
        bothCommitments = Both(
          buyer = new Hash(commitment.getBuyerTxId.toByteArray),
          seller = new Hash(commitment.getSellerTxId.toByteArray)
        )
      )

      override def toProtobuf(commitment: CommitmentNotification) =
        msg.CommitmentNotification.newBuilder
          .setExchangeId(commitment.exchangeId.value)
          .setBuyerTxId(ByteString.copyFrom(commitment.bothCommitments.buyer.getBytes))
          .setSellerTxId(ByteString.copyFrom(commitment.bothCommitments.seller.getBytes))
          .build
    }

  implicit val enterExchangeMapping = new ProtoMapping[ExchangeCommitment, msg.ExchangeCommitment] {

      override def fromProtobuf(enter: msg.ExchangeCommitment) = ExchangeCommitment(
        commitmentTransaction = txSerialization.deserializeTransaction(
          enter.getCommitmentTransaction),
        exchangeId = Exchange.Id(enter.getExchangeId)
      )

      override def toProtobuf(enter: ExchangeCommitment) = msg.ExchangeCommitment.newBuilder
        .setExchangeId(enter.exchangeId.value)
        .setCommitmentTransaction(txSerialization.serialize(enter.commitmentTransaction)).build
    }

  implicit val exchangeAbortedMapping = new ProtoMapping[ExchangeAborted, msg.ExchangeAborted] {

    override def fromProtobuf(exchangeAborted: msg.ExchangeAborted) = ExchangeAborted(
      exchangeId = Exchange.Id(exchangeAborted.getExchangeId),
      reason = exchangeAborted.getReason
    )

    override def toProtobuf(exchangeAborted: ExchangeAborted) = msg.ExchangeAborted.newBuilder
      .setExchangeId(exchangeAborted.exchangeId.value)
      .setReason(exchangeAborted.reason)
      .build
  }

  implicit val exchangeRejectionMapping = new ProtoMapping[ExchangeRejection, msg.ExchangeRejection] {

    override def fromProtobuf(rejection: msg.ExchangeRejection) = ExchangeRejection(
      exchangeId = Exchange.Id(rejection.getExchangeId),
      reason = rejection.getReason
    )

    override def toProtobuf(rejection: ExchangeRejection) = msg.ExchangeRejection.newBuilder
      .setExchangeId(rejection.exchangeId.value)
      .setReason(rejection.reason)
      .build
  }

  implicit val fiatAmountMapping = new ProtoMapping[CurrencyAmount[FiatCurrency], msg.FiatAmount] {

    override def fromProtobuf(amount: msg.FiatAmount): CurrencyAmount[FiatCurrency] =
      FiatCurrency(Currency.getInstance(amount.getCurrency)).amount(
        BigDecimal.valueOf(amount.getValue, amount.getScale))

    override def toProtobuf(amount: CurrencyAmount[FiatCurrency]): msg.FiatAmount =
      msg.FiatAmount.newBuilder
        .setValue(amount.value.underlying().unscaledValue.longValue)
        .setScale(amount.value.scale)
        .setCurrency(amount.currency.javaCurrency.getCurrencyCode)
        .build
  }

  implicit val btcAmountMapping = new ProtoMapping[BitcoinAmount, msg.BtcAmount] {

    override def fromProtobuf(amount: msg.BtcAmount): BitcoinAmount =
      Bitcoin.amount(BigDecimal.valueOf(amount.getValue, amount.getScale))

    override def toProtobuf(amount: BitcoinAmount): msg.BtcAmount = msg.BtcAmount.newBuilder
      .setValue(amount.value.underlying().unscaledValue.longValue)
      .setScale(amount.value.scale)
      .build
  }

  implicit val marketMapping = new ProtoMapping[Market[FiatCurrency], msg.Market] {

    override def fromProtobuf(market: msg.Market): Market[FiatCurrency] =
      Market(FiatCurrency(Currency.getInstance(market.getCurrency)))

    override def toProtobuf(market: Market[FiatCurrency]): msg.Market = msg.Market.newBuilder
      .setCurrency(market.currency.javaCurrency.getCurrencyCode)
      .build
  }

  implicit val orderBookEntryMapping = new ProtoMapping[OrderBookEntry[FiatAmount], msg.OrderBookEntry] {

    override def fromProtobuf(entry: msg.OrderBookEntry) = OrderBookEntry(
      id = OrderId(entry.getId),
      orderType = entry.getOrderType match {
        case msg.OrderBookEntry.OrderType.BID => Bid
        case msg.OrderBookEntry.OrderType.ASK => Ask
      },
      amount = ProtoMapping.fromProtobuf(entry.getAmount),
      price = ProtoMapping.fromProtobuf(entry.getPrice)
    )

    override def toProtobuf(entry: OrderBookEntry[FiatAmount]) = msg.OrderBookEntry.newBuilder
      .setId(entry.id.value)
      .setOrderType(entry.orderType match {
        case Bid => msg.OrderBookEntry.OrderType.BID
        case Ask => msg.OrderBookEntry.OrderType.ASK
      })
      .setAmount(ProtoMapping.toProtobuf(entry.amount))
      .setPrice(ProtoMapping.toProtobuf(entry.price))
      .build
  }

  implicit val peerPositionsMapping = new ProtoMapping[PeerOrderRequests[FiatCurrency],
      msg.PeerOrderRequests] {

    override def fromProtobuf(message: msg.PeerOrderRequests) = {
      val market = ProtoMapping.fromProtobuf(message.getMarket)
      val protobufEntries = message.getEntriesList.asScala
      val positions = for (protoEntry <- protobufEntries) yield {
        val pos = ProtoMapping.fromProtobuf[OrderBookEntry[FiatAmount], msg.OrderBookEntry](protoEntry)
        require(pos.price.currency == market.currency, s"Mixed currencies on $message")
        pos
      }
      PeerOrderRequests(market, positions)
    }

    override def toProtobuf(requests: PeerOrderRequests[FiatCurrency]) = {
      val builder = msg.PeerOrderRequests.newBuilder
        .setMarket(ProtoMapping.toProtobuf(requests.market))
      for (entry <- requests.entries) {
        builder.addEntries(ProtoMapping.toProtobuf[OrderBookEntry[FiatAmount], msg.OrderBookEntry](entry))
      }
      builder.build
    }
  }

  implicit val orderMatchMapping = new ProtoMapping[OrderMatch, msg.OrderMatch] {

    override def fromProtobuf(orderMatch: msg.OrderMatch): OrderMatch = OrderMatch(
      exchangeId = Exchange.Id(orderMatch.getExchangeId),
      amount = ProtoMapping.fromProtobuf(orderMatch.getAmount),
      price = ProtoMapping.fromProtobuf(orderMatch.getPrice),
      peers = Both(
        buyer = PeerId(orderMatch.getBuyer),
        seller = PeerId(orderMatch.getSeller)
      )
    )

    override def toProtobuf(orderMatch: OrderMatch): msg.OrderMatch = msg.OrderMatch.newBuilder
      .setExchangeId(orderMatch.exchangeId.value)
      .setAmount(ProtoMapping.toProtobuf(orderMatch.amount))
      .setPrice(ProtoMapping.toProtobuf(orderMatch.price))
      .setBuyer(orderMatch.peers.buyer.value)
      .setSeller(orderMatch.peers.seller.value)
      .build
  }

  implicit val quoteMapping = new ProtoMapping[Quote[FiatCurrency], msg.Quote] {

    override def fromProtobuf(quote: msg.Quote): Quote[FiatCurrency] = {
      val bidOption =
        if (quote.hasHighestBid) Some(ProtoMapping.fromProtobuf(quote.getHighestBid)) else None
      val askOption =
        if (quote.hasLowestAsk) Some(ProtoMapping.fromProtobuf(quote.getLowestAsk)) else None
      val lastPriceOption =
        if (quote.hasLastPrice) Some(ProtoMapping.fromProtobuf(quote.getLastPrice)) else None
      val market = ProtoMapping.fromProtobuf(quote.getMarket)
      def requireCorrectCurrency(amount: Option[CurrencyAmount[FiatCurrency]]): Unit = {
        require(amount.forall(_.currency == market.currency),
          s"Incorrect currency. Expected ${market.currency}, received ${amount.get.currency}")
      }
      requireCorrectCurrency(bidOption)
      requireCorrectCurrency(askOption)
      requireCorrectCurrency(lastPriceOption)
      Quote(market, bidOption -> askOption, lastPriceOption)
    }

    override def toProtobuf(quote: Quote[FiatCurrency]): msg.Quote = {
      val Quote(market, (bidOption, askOption), lastPriceOption) = quote
      val builder = msg.Quote.newBuilder.setMarket(ProtoMapping.toProtobuf(market))
      bidOption.foreach(bid => builder.setHighestBid(ProtoMapping.toProtobuf(bid)))
      askOption.foreach(ask => builder.setLowestAsk(ProtoMapping.toProtobuf(ask)))
      lastPriceOption.foreach(lastPrice => builder.setLastPrice(ProtoMapping.toProtobuf(lastPrice)))
      builder.build
    }
  }

  implicit val openOrdersRequestMapping = new ProtoMapping[OpenOrdersRequest, msg.OpenOrdersRequest] {

    override def fromProtobuf(openOrdersRequest: msg.OpenOrdersRequest): OpenOrdersRequest =
      OpenOrdersRequest(ProtoMapping.fromProtobuf(openOrdersRequest.getMarket))

    override def toProtobuf(openOrdersRequest: OpenOrdersRequest): msg.OpenOrdersRequest =
      msg.OpenOrdersRequest.newBuilder
        .setMarket(ProtoMapping.toProtobuf(openOrdersRequest.market))
        .build
  }

  implicit val openOrdersMapping = new ProtoMapping[OpenOrders[FiatCurrency], msg.OpenOrders] {

    override def fromProtobuf(openOrders: msg.OpenOrders): OpenOrders[FiatCurrency] =
      OpenOrders(ProtoMapping.fromProtobuf(openOrders.getOrders))

    override def toProtobuf(openOrders: OpenOrders[FiatCurrency]): msg.OpenOrders = {
      msg.OpenOrders.newBuilder
        .setOrders(ProtoMapping.toProtobuf[PeerOrderRequests[FiatCurrency], msg.PeerOrderRequests](
          openOrders.orders))
        .build
    }
  }

  implicit val quoteRequestMapping = new ProtoMapping[QuoteRequest, msg.QuoteRequest] {

    override def fromProtobuf(request: msg.QuoteRequest): QuoteRequest =
      QuoteRequest(ProtoMapping.fromProtobuf(request.getMarket))

    override def toProtobuf(request: QuoteRequest): msg.QuoteRequest = msg.QuoteRequest.newBuilder
      .setMarket(ProtoMapping.toProtobuf(request.market))
      .build
  }

  implicit val peerHandshakeMapping =
    new ProtoMapping[PeerHandshake, msg.PeerHandshake] {

      override def fromProtobuf(message: msg.PeerHandshake) = PeerHandshake(
        exchangeId = Exchange.Id(message.getExchangeId),
        paymentProcessorAccount = message.getPaymentProcessorAccount,
        publicKey = txSerialization.deserializePublicKey(message.getPublicKey)
      )

      override def toProtobuf(message: PeerHandshake) =
        msg.PeerHandshake.newBuilder
          .setExchangeId(message.exchangeId.value)
          .setPublicKey(txSerialization.serialize(message.publicKey))
          .setPaymentProcessorAccount(message.paymentProcessorAccount)
          .build
    }

  implicit object RefundSignatureRequestMapping
    extends ProtoMapping[RefundSignatureRequest, msg.RefundSignatureRequest] {

    override def fromProtobuf(message: msg.RefundSignatureRequest) = RefundSignatureRequest(
      exchangeId = Exchange.Id(message.getExchangeId),
      refundTx = txSerialization.deserializeTransaction(message.getRefundTx)
    )

    override def toProtobuf(request: RefundSignatureRequest): msg.RefundSignatureRequest =
      msg.RefundSignatureRequest.newBuilder
        .setExchangeId(request.exchangeId.value)
        .setRefundTx(txSerialization.serialize(request.refundTx))
        .build
  }

  implicit val refundSignatureResponseMapping =
    new ProtoMapping[RefundSignatureResponse, msg.RefundSignatureResponse] {

      override def fromProtobuf(response: msg.RefundSignatureResponse) = RefundSignatureResponse(
        exchangeId = Exchange.Id(response.getExchangeId),
        refundSignature = txSerialization.deserializeSignature(response.getTransactionSignature)
      )

      override def toProtobuf(response: RefundSignatureResponse) =
        msg.RefundSignatureResponse.newBuilder
          .setExchangeId(response.exchangeId.value)
          .setTransactionSignature(txSerialization.serialize(response.refundSignature))
          .build()
    }

  implicit val offerSignatureMapping = new ProtoMapping[StepSignatures, msg.StepSignature] {

    override def fromProtobuf(message: msg.StepSignature) = StepSignatures(
      exchangeId = Exchange.Id(message.getExchangeId),
      step = message.getStep,
      signatures = Signatures(
        buyer = txSerialization.deserializeSignature(message.getBuyerDepositSignature),
        seller = txSerialization.deserializeSignature(message.getSellerDepositSignature)
      )
    )

    override def toProtobuf(obj: StepSignatures) = msg.StepSignature.newBuilder
      .setExchangeId(obj.exchangeId.value)
      .setStep(obj.step)
      .setBuyerDepositSignature(txSerialization.serialize(obj.signatures.buyer))
      .setSellerDepositSignature(txSerialization.serialize(obj.signatures.seller))
      .build()
  }

  implicit val paymentProofMapping = new ProtoMapping[PaymentProof, msg.PaymentProof] {

    override def fromProtobuf(message: msg.PaymentProof) = PaymentProof(
      exchangeId = Exchange.Id(message.getExchangeId),
      paymentId = message.getPaymentId
    )

    override def toProtobuf(obj: PaymentProof): msg.PaymentProof = msg.PaymentProof.newBuilder
      .setExchangeId(obj.exchangeId.value)
      .setPaymentId(obj.paymentId)
      .build()
  }
}
