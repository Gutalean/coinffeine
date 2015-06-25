package coinffeine.protocol.serialization.protobuf

import scala.collection.JavaConverters._

import com.google.protobuf.ByteString

import coinffeine.model.Both
import coinffeine.model.bitcoin.Hash
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.model.order._
import coinffeine.protocol.messages.arbitration._
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.DecimalNumber
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => msg}
import coinffeine.protocol.serialization.TransactionSerialization

/** Implicit conversion mappings for the protocol messages */
private class ProtoMappings(txSerialization: TransactionSerialization) {

  implicit val peerIdMapping = new ProtoMapping[PeerId, msg.PeerId] {

    override def fromProtobuf(message: msg.PeerId) = PeerId(message.getValue)

    override def toProtobuf(peerId: PeerId) = msg.PeerId.newBuilder()
      .setValue(peerId.value)
      .build()
  }

  implicit val orderIdMapping = new ProtoMapping[OrderId, msg.OrderId] {

    override def fromProtobuf(message: msg.OrderId) = OrderId(message.getValue)

    override def toProtobuf(orderId: OrderId) = msg.OrderId.newBuilder()
      .setValue(orderId.value)
      .build()
  }

  implicit val exchangeIdMapping = new ProtoMapping[ExchangeId, msg.ExchangeId] {

    override def fromProtobuf(message: msg.ExchangeId) = ExchangeId(message.getValue)

    override def toProtobuf(exchangeId: ExchangeId) = msg.ExchangeId.newBuilder()
      .setValue(exchangeId.value)
      .build()
  }

  implicit val commitmentNotificationMapping =
    new ProtoMapping[CommitmentNotification, msg.CommitmentNotification] {

      override def fromProtobuf(commitment: msg.CommitmentNotification) = CommitmentNotification(
        exchangeId = ProtoMapping.fromProtobuf(commitment.getExchangeId),
        bothCommitments = Both(
          buyer = new Hash(commitment.getBuyerTxId.toByteArray),
          seller = new Hash(commitment.getSellerTxId.toByteArray)
        )
      )

      override def toProtobuf(commitment: CommitmentNotification) =
        msg.CommitmentNotification.newBuilder
          .setExchangeId(ProtoMapping.toProtobuf(commitment.exchangeId))
          .setBuyerTxId(ByteString.copyFrom(commitment.bothCommitments.buyer.getBytes))
          .setSellerTxId(ByteString.copyFrom(commitment.bothCommitments.seller.getBytes))
          .build
    }

  implicit val commitmentNotificationAckMapping =
    new ProtoMapping[CommitmentNotificationAck, msg.CommitmentNotificationAck] {

      override def fromProtobuf(commitment: msg.CommitmentNotificationAck) =
        CommitmentNotificationAck(ProtoMapping.fromProtobuf(commitment.getExchangeId))

      override def toProtobuf(commitment: CommitmentNotificationAck) =
        msg.CommitmentNotificationAck.newBuilder
          .setExchangeId(ProtoMapping.toProtobuf(commitment.exchangeId))
          .build
    }

  implicit val exchangeCommitmentMapping = new ProtoMapping[ExchangeCommitment, msg.ExchangeCommitment] {

    override def fromProtobuf(message: msg.ExchangeCommitment) = ExchangeCommitment(
      exchangeId = ProtoMapping.fromProtobuf(message.getExchangeId),
      publicKey = txSerialization.deserializePublicKey(message.getPublicKey),
      commitmentTransaction = txSerialization.deserializeTransaction(message.getCommitmentTransaction)
    )

    override def toProtobuf(message: ExchangeCommitment) = msg.ExchangeCommitment.newBuilder
      .setExchangeId(ProtoMapping.toProtobuf(message.exchangeId))
      .setPublicKey(txSerialization.serialize(message.publicKey))
      .setCommitmentTransaction(txSerialization.serialize(message.commitmentTransaction)).build
  }

  private val exchangeRejectionCauseMapping =
    Map[ExchangeRejection.Cause, msg.ExchangeRejection.Cause](
      ExchangeRejection.CounterpartTimeout -> msg.ExchangeRejection.Cause.COUNTERPART_TIMEOUT,
      ExchangeRejection.UnavailableFunds -> msg.ExchangeRejection.Cause.UNAVAILABLE_FUNDS,
      ExchangeRejection.InvalidOrderMatch -> msg.ExchangeRejection.Cause.INVALID_ORDER_MATCH
    )

  private def exchangeRejectionCauseReverseMapping(message: msg.ExchangeRejection.Cause) = {
    exchangeRejectionCauseMapping.collectFirst {
      case (cause, protoCause) if protoCause == message => cause
    }.getOrElse(throw new scala.NoSuchElementException(s"Unsupported rejection cause: $message"))
  }

  implicit val exchangeRejectionMapping = new ProtoMapping[ExchangeRejection, msg.ExchangeRejection] {

    override def fromProtobuf(rejection: msg.ExchangeRejection) = ExchangeRejection(
      exchangeId = ProtoMapping.fromProtobuf(rejection.getExchangeId),
      cause = exchangeRejectionCauseReverseMapping(rejection.getCause)
    )

    override def toProtobuf(rejection: ExchangeRejection) = msg.ExchangeRejection.newBuilder
      .setExchangeId(ProtoMapping.toProtobuf(rejection.exchangeId))
      .setCause(exchangeRejectionCauseMapping(rejection.cause))
      .build
  }

  implicit val exchangeAbortedMapping = new ProtoMapping[ExchangeAborted, msg.ExchangeAborted] {

    override def fromProtobuf(exchangeAborted: msg.ExchangeAborted) = ExchangeAborted(
      exchangeId = ProtoMapping.fromProtobuf(exchangeAborted.getExchangeId),
      cause = exchangeAborted.getCause match {

        case msg.ExchangeAborted.Cause.TIMEOUT => ExchangeAborted.Timeout

        case msg.ExchangeAborted.Cause.INVALID_COMMITMENTS =>
          val culprits: Set[PeerId] = exchangeAborted.getCulpritsList.asScala
            .map(peerId => ProtoMapping.fromProtobuf(peerId))
            .toSet
          ExchangeAborted.InvalidCommitments(culprits)

        case msg.ExchangeAborted.Cause.PUBLICATION_FAILURE =>
          require(exchangeAborted.hasTxId, "Missing field txId")
          ExchangeAborted.PublicationFailure(new Hash(exchangeAborted.getTxId.toByteArray))

        case msg.ExchangeAborted.Cause.REJECTED =>
          require(exchangeAborted.hasRejectionCause, "Missing field rejectionCause")
          ExchangeAborted.Rejected(
            exchangeRejectionCauseReverseMapping(exchangeAborted.getRejectionCause))
      }
    )

    override def toProtobuf(exchangeAborted: ExchangeAborted) = {
      val builder = msg.ExchangeAborted.newBuilder
        .setExchangeId(ProtoMapping.toProtobuf(exchangeAborted.exchangeId))
      exchangeAborted.cause match {

        case ExchangeAborted.Timeout =>
          builder.setCause(msg.ExchangeAborted.Cause.TIMEOUT)

        case ExchangeAborted.InvalidCommitments(culprits) =>
          builder.setCause(msg.ExchangeAborted.Cause.INVALID_COMMITMENTS)
          builder.addAllCulprits(culprits.map(peerId => ProtoMapping.toProtobuf(peerId)).asJava)

        case ExchangeAborted.PublicationFailure(txId) =>
          builder.setCause(msg.ExchangeAborted.Cause.PUBLICATION_FAILURE)
          builder.setTxId(ByteString.copyFrom(txId.getBytes))

        case ExchangeAborted.Rejected(rejectionCause) =>
          builder.setCause(msg.ExchangeAborted.Cause.REJECTED)
          builder.setRejectionCause(exchangeRejectionCauseMapping(rejectionCause))
      }
      builder.build()
    }
  }

  implicit val decimalNumberMapping = new ProtoMapping[BigDecimal, msg.DecimalNumber] {

    override def fromProtobuf(amount: msg.DecimalNumber): BigDecimal =
      BigDecimal(BigInt(amount.getValue.toByteArray), amount.getScale)

    override def toProtobuf(amount: BigDecimal): msg.DecimalNumber = msg.DecimalNumber.newBuilder
      .setScale(amount.scale)
      .setValue(ByteString.copyFrom(amount.underlying().unscaledValue.toByteArray))
      .build
  }

  implicit val fiatAmountMapping = new ProtoMapping[FiatAmount, msg.FiatAmount] {

    override def fromProtobuf(amount: msg.FiatAmount): FiatAmount =
      FiatCurrency(amount.getCurrency)(decimalNumberMapping.fromProtobuf(amount.getAmount))

    override def toProtobuf(amount: FiatAmount): msg.FiatAmount =
      msg.FiatAmount.newBuilder
        .setAmount(decimalNumberMapping.toProtobuf(amount.value))
        .setCurrency(amount.currency.javaCurrency.getCurrencyCode)
        .build
  }

  implicit val priceMapping = new ProtoMapping[OrderPrice, msg.Price] {

    override def fromProtobuf(price: msg.Price) = {
      val currency = FiatCurrency(price.getCurrency)
      if (price.hasLimit) LimitPrice(Price(ProtoMapping.fromProtobuf(price.getLimit), currency))
      else MarketPrice(currency)
    }

    override def toProtobuf(price: OrderPrice): msg.Price = {
      val builder = msg.Price.newBuilder.setCurrency(price.currency.javaCurrency.getCurrencyCode)
      price.toOption.foreach { limit =>
        builder.setLimit(ProtoMapping.toProtobuf(limit.value))
      }
      builder.build
    }
  }

  implicit val marketMapping = new ProtoMapping[Market, msg.Market] {

    override def fromProtobuf(market: msg.Market): Market =
      Market(FiatCurrency(market.getCurrency))

    override def toProtobuf(market: Market): msg.Market = msg.Market.newBuilder
      .setCurrency(market.currency.javaCurrency.getCurrencyCode)
      .build
  }

  implicit val orderBookEntryMapping = new ProtoMapping[OrderBookEntry, msg.OrderBookEntry] {

    override def fromProtobuf(entry: msg.OrderBookEntry) = OrderBookEntry(
      id = ProtoMapping.fromProtobuf(entry.getId),
      orderType = entry.getOrderType match {
        case msg.OrderBookEntry.OrderType.BID => Bid
        case msg.OrderBookEntry.OrderType.ASK => Ask
      },
      amount = Bitcoin(decimalNumberMapping.fromProtobuf(entry.getAmount)),
      price = priceMapping.fromProtobuf(entry.getPrice)
    )

    override def toProtobuf(entry: OrderBookEntry) = msg.OrderBookEntry.newBuilder
      .setId(ProtoMapping.toProtobuf(entry.id))
      .setOrderType(entry.orderType match {
        case Bid => msg.OrderBookEntry.OrderType.BID
        case Ask => msg.OrderBookEntry.OrderType.ASK
      })
      .setAmount(decimalNumberMapping.toProtobuf(entry.amount.value))
      .setPrice(priceMapping.toProtobuf(entry.price))
      .build()
  }

  implicit val peerPositionsMapping = new ProtoMapping[PeerPositions, msg.PeerPositions] {

    override def fromProtobuf(message: msg.PeerPositions): PeerPositions = {
      val market = marketMapping.fromProtobuf(message.getMarket)
      val protobufEntries = message.getEntriesList.asScala
      val positions = for (protoEntry <- protobufEntries) yield {
        val pos = orderBookEntryMapping.fromProtobuf(protoEntry)
        require(pos.price.currency == market.currency, s"Mixed currencies on $message")
        pos.asInstanceOf[OrderBookEntry]
      }
      PeerPositions(market.asInstanceOf[Market], positions, message.getNonce)
    }

    override def toProtobuf(positions: PeerPositions) = {
      val builder = msg.PeerPositions.newBuilder
        .setMarket(marketMapping.toProtobuf(positions.market))
        .setNonce(positions.nonce)
      for (entry <- positions.entries) {
        builder.addEntries(orderBookEntryMapping.toProtobuf(entry))
      }
      builder.build
    }
  }

  implicit val peerPositionsReceivedMapping = new ProtoMapping[PeerPositionsReceived,
    msg.PeerPositionsReceived] {

    override def fromProtobuf(message: msg.PeerPositionsReceived) =
      PeerPositionsReceived(message.getNonce)

    override def toProtobuf(reception: PeerPositionsReceived) = msg.PeerPositionsReceived.newBuilder
      .setNonce(reception.nonce)
      .build()
  }

  implicit val orderMatchMapping = new ProtoMapping[OrderMatch, msg.OrderMatch] {

    override def fromProtobuf(orderMatch: msg.OrderMatch) = {
      val currency = FiatCurrency(orderMatch.getCurrency)
      OrderMatch(
        orderId = ProtoMapping.fromProtobuf(orderMatch.getOrderId),
        exchangeId = ProtoMapping.fromProtobuf(orderMatch.getExchangeId),
        bitcoinAmount = Both(
          buyer = Bitcoin(ProtoMapping.fromProtobuf(orderMatch.getBuyerBitcoinAmount)),
          seller = Bitcoin(ProtoMapping.fromProtobuf(orderMatch.getSellerBitcoinAmount))
        ),
        fiatAmount = Both(
          buyer = currency.exactAmount(
            ProtoMapping.fromProtobuf(orderMatch.getBuyerFiatAmount)),
          seller = currency.exactAmount(
            ProtoMapping.fromProtobuf(orderMatch.getSellerFiatAmount))
        ),
        lockTime = orderMatch.getLockTime,
        counterpart = ProtoMapping.fromProtobuf(orderMatch.getCounterpart)
      )
    }

    override def toProtobuf(orderMatch: OrderMatch): msg.OrderMatch =
      msg.OrderMatch.newBuilder
        .setOrderId(ProtoMapping.toProtobuf(orderMatch.orderId))
        .setExchangeId(ProtoMapping.toProtobuf(orderMatch.exchangeId))
        .setCurrency(orderMatch.currency.javaCurrency.getCurrencyCode)
        .setBuyerBitcoinAmount(ProtoMapping.toProtobuf(orderMatch.bitcoinAmount.buyer.value))
        .setSellerBitcoinAmount(ProtoMapping.toProtobuf(orderMatch.bitcoinAmount.seller.value))
        .setBuyerFiatAmount(ProtoMapping.toProtobuf(orderMatch.fiatAmount.buyer.value))
        .setSellerFiatAmount(ProtoMapping.toProtobuf(orderMatch.fiatAmount.seller.value))
        .setLockTime(orderMatch.lockTime)
        .setCounterpart(ProtoMapping.toProtobuf(orderMatch.counterpart))
        .build
  }

  implicit val quoteMapping = new ProtoMapping[Quote, msg.Quote] {

    override def fromProtobuf(quote: msg.Quote): Quote = buildQuote(
      market = marketMapping.fromProtobuf(quote.getMarket),
      bidOption = if (quote.hasHighestBid) Some(quote.getHighestBid) else None,
      askOption = if (quote.hasLowestAsk) Some(quote.getLowestAsk) else None,
      lastPriceOption = if (quote.hasLastPrice) Some(quote.getLastPrice) else None
    )

    private def buildQuote(market: Market,
                                              bidOption: Option[DecimalNumber],
                                              askOption: Option[DecimalNumber],
                                              lastPriceOption: Option[DecimalNumber]): Quote = {
      def toPrice(amountOpt: Option[DecimalNumber]) = {
        amountOpt.map { amount => Price(ProtoMapping.fromProtobuf(amount), market.currency)}
      }
      Quote(market, Spread(toPrice(bidOption), toPrice(askOption)), toPrice(lastPriceOption))
    }

    override def toProtobuf(quote: Quote): msg.Quote = {
      val Quote(market, Spread(bidOption, askOption), lastPriceOption) = quote
      val builder = msg.Quote.newBuilder.setMarket(marketMapping.toProtobuf(market))
      bidOption.foreach(bid => builder.setHighestBid(ProtoMapping.toProtobuf(bid.value)))
      askOption.foreach(ask => builder.setLowestAsk(ProtoMapping.toProtobuf(ask.value)))
      lastPriceOption.foreach(lastPrice =>
        builder.setLastPrice(ProtoMapping.toProtobuf(lastPrice.value)))
      builder.build
    }
  }

  implicit val openOrdersRequestMapping = new ProtoMapping[OpenOrdersRequest, msg.OpenOrdersRequest] {

    override def fromProtobuf(openOrdersRequest: msg.OpenOrdersRequest): OpenOrdersRequest =
      OpenOrdersRequest(ProtoMapping.fromProtobuf(openOrdersRequest.getMarket))

    override def toProtobuf(openOrdersRequest: OpenOrdersRequest): msg.OpenOrdersRequest =
      msg.OpenOrdersRequest.newBuilder
        .setMarket(marketMapping.toProtobuf(openOrdersRequest.market))
        .build
  }

  implicit val openOrdersMapping = new ProtoMapping[OpenOrders, msg.OpenOrders] {

    override def fromProtobuf(openOrders: msg.OpenOrders): OpenOrders =
      OpenOrders(ProtoMapping.fromProtobuf(openOrders.getOrders))

    override def toProtobuf(openOrders: OpenOrders): msg.OpenOrders = {
      msg.OpenOrders.newBuilder
        .setOrders(ProtoMapping.toProtobuf[PeerPositions, msg.PeerPositions](
          openOrders.orders))
        .build
    }
  }

  implicit val quoteRequestMapping = new ProtoMapping[QuoteRequest, msg.QuoteRequest] {

    override def fromProtobuf(request: msg.QuoteRequest): QuoteRequest =
      QuoteRequest(ProtoMapping.fromProtobuf(request.getMarket))

    override def toProtobuf(request: QuoteRequest): msg.QuoteRequest = msg.QuoteRequest.newBuilder
      .setMarket(marketMapping.toProtobuf(request.market))
      .build
  }

  implicit val peerHandshakeMapping =
    new ProtoMapping[PeerHandshake, msg.PeerHandshake] {

      override def fromProtobuf(message: msg.PeerHandshake) = PeerHandshake(
        exchangeId = ProtoMapping.fromProtobuf(message.getExchangeId),
        paymentProcessorAccount = message.getPaymentProcessorAccount,
        publicKey = txSerialization.deserializePublicKey(message.getPublicKey)
      )

      override def toProtobuf(message: PeerHandshake) =
        msg.PeerHandshake.newBuilder
          .setExchangeId(ProtoMapping.toProtobuf(message.exchangeId))
          .setPublicKey(txSerialization.serialize(message.publicKey))
          .setPaymentProcessorAccount(message.paymentProcessorAccount)
          .build
    }

  implicit object RefundSignatureRequestMapping
    extends ProtoMapping[RefundSignatureRequest, msg.RefundSignatureRequest] {

    override def fromProtobuf(message: msg.RefundSignatureRequest) = RefundSignatureRequest(
      exchangeId = ProtoMapping.fromProtobuf(message.getExchangeId),
      refundTx = txSerialization.deserializeTransaction(message.getRefundTx)
    )

    override def toProtobuf(request: RefundSignatureRequest): msg.RefundSignatureRequest =
      msg.RefundSignatureRequest.newBuilder
        .setExchangeId(ProtoMapping.toProtobuf(request.exchangeId))
        .setRefundTx(txSerialization.serialize(request.refundTx))
        .build
  }

  implicit val refundSignatureResponseMapping =
    new ProtoMapping[RefundSignatureResponse, msg.RefundSignatureResponse] {

      override def fromProtobuf(response: msg.RefundSignatureResponse) = RefundSignatureResponse(
        exchangeId = ProtoMapping.fromProtobuf(response.getExchangeId),
        refundSignature = txSerialization.deserializeSignature(response.getTransactionSignature)
      )

      override def toProtobuf(response: RefundSignatureResponse) =
        msg.RefundSignatureResponse.newBuilder
          .setExchangeId(ProtoMapping.toProtobuf(response.exchangeId))
          .setTransactionSignature(txSerialization.serialize(response.refundSignature))
          .build()
    }

  implicit val offerSignatureMapping = new ProtoMapping[StepSignatures, msg.StepSignature] {

    override def fromProtobuf(message: msg.StepSignature) = StepSignatures(
      exchangeId = ProtoMapping.fromProtobuf(message.getExchangeId),
      step = message.getStep,
      signatures = Both(
        buyer = txSerialization.deserializeSignature(message.getBuyerDepositSignature),
        seller = txSerialization.deserializeSignature(message.getSellerDepositSignature)
      )
    )

    override def toProtobuf(obj: StepSignatures) = msg.StepSignature.newBuilder
      .setExchangeId(ProtoMapping.toProtobuf(obj.exchangeId))
      .setStep(obj.step)
      .setBuyerDepositSignature(txSerialization.serialize(obj.signatures.buyer))
      .setSellerDepositSignature(txSerialization.serialize(obj.signatures.seller))
      .build()
  }

  implicit val paymentProofMapping = new ProtoMapping[PaymentProof, msg.PaymentProof] {

    override def fromProtobuf(message: msg.PaymentProof) = PaymentProof(
      exchangeId = ProtoMapping.fromProtobuf(message.getExchangeId),
      paymentId = message.getPaymentId,
      step = message.getStep
    )

    override def toProtobuf(obj: PaymentProof): msg.PaymentProof = msg.PaymentProof.newBuilder
      .setExchangeId(ProtoMapping.toProtobuf(obj.exchangeId))
      .setPaymentId(obj.paymentId)
      .setStep(obj.step)
      .build()
  }

  implicit val micropaymentChannelClosedMapping =
    new ProtoMapping[MicropaymentChannelClosed, msg.MicropaymentChannelClosed] {

      override def fromProtobuf(message: msg.MicropaymentChannelClosed) =
        MicropaymentChannelClosed(ProtoMapping.fromProtobuf(message.getExchangeId))

      override def toProtobuf(obj: MicropaymentChannelClosed) =
        msg.MicropaymentChannelClosed.newBuilder()
          .setExchangeId(ProtoMapping.toProtobuf(obj.exchangeId))
          .build()
    }
}
