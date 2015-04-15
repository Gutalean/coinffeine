package coinffeine.protocol.serialization.protobuf

import scala.collection.JavaConverters._

import com.google.protobuf.ByteString

import coinffeine.model.Both
import coinffeine.model.bitcoin.Hash
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.arbitration._
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._
import coinffeine.protocol.protobuf.CoinffeineProtobuf.DecimalNumber
import coinffeine.protocol.protobuf.{CoinffeineProtobuf => msg}
import coinffeine.protocol.serialization.TransactionSerialization

/** Implicit conversion mappings for the protocol messages */
private class ProtoMappings(txSerialization: TransactionSerialization) {

  implicit val commitmentNotificationMapping =
    new ProtoMapping[CommitmentNotification, msg.CommitmentNotification] {

      override def fromProtobuf(commitment: msg.CommitmentNotification) = CommitmentNotification(
        exchangeId = ExchangeId(commitment.getExchangeId),
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

  implicit val commitmentNotificationAckMapping =
    new ProtoMapping[CommitmentNotificationAck, msg.CommitmentNotificationAck] {

      override def fromProtobuf(commitment: msg.CommitmentNotificationAck) =
        CommitmentNotificationAck(ExchangeId(commitment.getExchangeId))

      override def toProtobuf(commitment: CommitmentNotificationAck) =
        msg.CommitmentNotificationAck.newBuilder
          .setExchangeId(commitment.exchangeId.value)
          .build
    }

  implicit val exchangeCommitmentMapping = new ProtoMapping[ExchangeCommitment, msg.ExchangeCommitment] {

    override def fromProtobuf(message: msg.ExchangeCommitment) = ExchangeCommitment(
      exchangeId = ExchangeId(message.getExchangeId),
      publicKey = txSerialization.deserializePublicKey(message.getPublicKey),
      commitmentTransaction = txSerialization.deserializeTransaction(message.getCommitmentTransaction)
    )

    override def toProtobuf(message: ExchangeCommitment) = msg.ExchangeCommitment.newBuilder
      .setExchangeId(message.exchangeId.value)
      .setPublicKey(txSerialization.serialize(message.publicKey))
      .setCommitmentTransaction(txSerialization.serialize(message.commitmentTransaction)).build
  }

  implicit val exchangeAbortedMapping = new ProtoMapping[ExchangeAborted, msg.ExchangeAborted] {

    override def fromProtobuf(exchangeAborted: msg.ExchangeAborted) = ExchangeAborted(
      exchangeId = ExchangeId(exchangeAborted.getExchangeId),
      reason = exchangeAborted.getReason
    )

    override def toProtobuf(exchangeAborted: ExchangeAborted) = msg.ExchangeAborted.newBuilder
      .setExchangeId(exchangeAborted.exchangeId.value)
      .setReason(exchangeAborted.reason)
      .build
  }

  implicit val exchangeRejectionMapping = new ProtoMapping[ExchangeRejection, msg.ExchangeRejection] {

    override def fromProtobuf(rejection: msg.ExchangeRejection) = ExchangeRejection(
      exchangeId = ExchangeId(rejection.getExchangeId),
      reason = rejection.getReason
    )

    override def toProtobuf(rejection: ExchangeRejection) = msg.ExchangeRejection.newBuilder
      .setExchangeId(rejection.exchangeId.value)
      .setReason(rejection.reason)
      .build
  }

  implicit val decimalNumberMapping = new ProtoMapping[BigDecimal, msg.DecimalNumber] {

    override def fromProtobuf(amount: msg.DecimalNumber): BigDecimal =
      BigDecimal(amount.getValue, amount.getScale)

    override def toProtobuf(amount: BigDecimal): msg.DecimalNumber = msg.DecimalNumber.newBuilder
      .setValue(amount.underlying().unscaledValue.longValue)
      .setScale(amount.scale)
      .build
  }

  implicit val fiatAmountMapping = new ProtoMapping[FiatAmount, msg.FiatAmount] {

    override def fromProtobuf(amount: msg.FiatAmount): FiatAmount =
      FiatAmount(decimalNumberMapping.fromProtobuf(amount.getAmount), amount.getCurrency)

    override def toProtobuf(amount: FiatAmount): msg.FiatAmount =
      msg.FiatAmount.newBuilder
        .setAmount(decimalNumberMapping.toProtobuf(amount.value))
        .setCurrency(amount.currency.javaCurrency.getCurrencyCode)
        .build
  }

  implicit val priceMapping = new ProtoMapping[OrderPrice[_ <: FiatCurrency], msg.Price] {

    override def fromProtobuf(price: msg.Price) = {
      val currency = FiatCurrency(price.getCurrency)
      if (price.hasLimit) LimitPrice(Price(ProtoMapping.fromProtobuf(price.getLimit), currency))
      else MarketPrice(currency)
    }

    override def toProtobuf(price: OrderPrice[_ <: FiatCurrency]): msg.Price = {
      val builder = msg.Price.newBuilder.setCurrency(price.currency.javaCurrency.getCurrencyCode)
      price.toOption.foreach { limit =>
        builder.setLimit(ProtoMapping.toProtobuf(limit.value))
      }
      builder.build
    }
  }

  implicit val marketMapping = new ProtoMapping[Market[_ <: FiatCurrency], msg.Market] {

    override def fromProtobuf(market: msg.Market): Market[_ <: FiatCurrency] =
      Market(FiatCurrency(market.getCurrency))

    override def toProtobuf(market: Market[_ <: FiatCurrency]): msg.Market = msg.Market.newBuilder
      .setCurrency(market.currency.javaCurrency.getCurrencyCode)
      .build
  }

  implicit val orderBookEntryMapping = new ProtoMapping[OrderBookEntry[_ <: FiatCurrency], msg.OrderBookEntry] {

    override def fromProtobuf(entry: msg.OrderBookEntry) = OrderBookEntry(
      id = OrderId(entry.getId),
      orderType = entry.getOrderType match {
        case msg.OrderBookEntry.OrderType.BID => Bid
        case msg.OrderBookEntry.OrderType.ASK => Ask
      },
      amount = Bitcoin(decimalNumberMapping.fromProtobuf(entry.getAmount)),
      price = priceMapping.fromProtobuf(entry.getPrice)
    )

    override def toProtobuf(entry: OrderBookEntry[_ <: FiatCurrency]) = msg.OrderBookEntry.newBuilder
      .setId(entry.id.value)
      .setOrderType(entry.orderType match {
        case Bid => msg.OrderBookEntry.OrderType.BID
        case Ask => msg.OrderBookEntry.OrderType.ASK
      })
      .setAmount(decimalNumberMapping.toProtobuf(entry.amount.value))
      .setPrice(priceMapping.toProtobuf(entry.price))
      .build()
  }

  implicit val peerPositionsMapping = new ProtoMapping[PeerPositions[_ <: FiatCurrency],
      msg.PeerPositions] {

    override def fromProtobuf(message: msg.PeerPositions): PeerPositions[_ <: FiatCurrency] = {
      val market = marketMapping.fromProtobuf(message.getMarket)
      val protobufEntries = message.getEntriesList.asScala
      val positions = for (protoEntry <- protobufEntries) yield {
        val pos = orderBookEntryMapping.fromProtobuf(protoEntry)
        require(pos.price.currency == market.currency, s"Mixed currencies on $message")
        pos.asInstanceOf[OrderBookEntry[market.currency.type]]
      }
      PeerPositions(market.asInstanceOf[Market[market.currency.type]], positions, message.getNonce)
    }

    override def toProtobuf(positions: PeerPositions[_ <: FiatCurrency]) = {
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

  implicit val orderMatchMapping = new ProtoMapping[OrderMatch[_ <: FiatCurrency], msg.OrderMatch] {

    override def fromProtobuf(orderMatch: msg.OrderMatch) = {
      val currency = FiatCurrency(orderMatch.getCurrency)
      OrderMatch(
        orderId = OrderId(orderMatch.getOrderId),
        exchangeId = ExchangeId(orderMatch.getExchangeId),
        bitcoinAmount = Both(
          buyer = Bitcoin(ProtoMapping.fromProtobuf(orderMatch.getBuyerBitcoinAmount)),
          seller = Bitcoin(ProtoMapping.fromProtobuf(orderMatch.getSellerBitcoinAmount))
        ),
        fiatAmount = Both(
          buyer = CurrencyAmount.exactAmount(
            ProtoMapping.fromProtobuf(orderMatch.getBuyerFiatAmount), currency),
          seller = CurrencyAmount.exactAmount(
            ProtoMapping.fromProtobuf(orderMatch.getSellerFiatAmount), currency)
        ),
        lockTime = orderMatch.getLockTime,
        counterpart = PeerId(orderMatch.getCounterpart)
      )
    }

    override def toProtobuf(orderMatch: OrderMatch[_ <: FiatCurrency]): msg.OrderMatch =
      msg.OrderMatch.newBuilder
        .setOrderId(orderMatch.orderId.value)
        .setExchangeId(orderMatch.exchangeId.value)
        .setCurrency(orderMatch.currency.javaCurrency.getCurrencyCode)
        .setBuyerBitcoinAmount(ProtoMapping.toProtobuf(orderMatch.bitcoinAmount.buyer.value))
        .setSellerBitcoinAmount(ProtoMapping.toProtobuf(orderMatch.bitcoinAmount.seller.value))
        .setBuyerFiatAmount(ProtoMapping.toProtobuf(orderMatch.fiatAmount.buyer.value))
        .setSellerFiatAmount(ProtoMapping.toProtobuf(orderMatch.fiatAmount.seller.value))
        .setLockTime(orderMatch.lockTime)
        .setCounterpart(orderMatch.counterpart.value)
        .build
  }

  implicit val quoteMapping = new ProtoMapping[Quote[_ <: FiatCurrency], msg.Quote] {

    override def fromProtobuf(quote: msg.Quote): Quote[_ <: FiatCurrency] = buildQuote(
      market = marketMapping.fromProtobuf(quote.getMarket),
      bidOption = if (quote.hasHighestBid) Some(quote.getHighestBid) else None,
      askOption = if (quote.hasLowestAsk) Some(quote.getLowestAsk) else None,
      lastPriceOption = if (quote.hasLastPrice) Some(quote.getLastPrice) else None
    )

    private def buildQuote[C <: FiatCurrency](market: Market[C],
                                              bidOption: Option[DecimalNumber],
                                              askOption: Option[DecimalNumber],
                                              lastPriceOption: Option[DecimalNumber]): Quote[C] = {
      def toPrice(amountOpt: Option[DecimalNumber]) = {
        amountOpt.map { amount => Price(ProtoMapping.fromProtobuf(amount), market.currency)}
      }
      Quote(market, Spread(toPrice(bidOption), toPrice(askOption)), toPrice(lastPriceOption))
    }

    override def toProtobuf(quote: Quote[_ <: FiatCurrency]): msg.Quote = {
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

  implicit val openOrdersMapping = new ProtoMapping[OpenOrders[_ <: FiatCurrency], msg.OpenOrders] {

    override def fromProtobuf(openOrders: msg.OpenOrders): OpenOrders[_ <: FiatCurrency] =
      OpenOrders(ProtoMapping.fromProtobuf(openOrders.getOrders))

    override def toProtobuf(openOrders: OpenOrders[_ <: FiatCurrency]): msg.OpenOrders = {
      msg.OpenOrders.newBuilder
        .setOrders(ProtoMapping.toProtobuf[PeerPositions[_ <: FiatCurrency], msg.PeerPositions](
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
        exchangeId = ExchangeId(message.getExchangeId),
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
      exchangeId = ExchangeId(message.getExchangeId),
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
        exchangeId = ExchangeId(response.getExchangeId),
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
      exchangeId = ExchangeId(message.getExchangeId),
      step = message.getStep,
      signatures = Both(
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
      exchangeId = ExchangeId(message.getExchangeId),
      paymentId = message.getPaymentId,
      step = message.getStep
    )

    override def toProtobuf(obj: PaymentProof): msg.PaymentProof = msg.PaymentProof.newBuilder
      .setExchangeId(obj.exchangeId.value)
      .setPaymentId(obj.paymentId)
      .setStep(obj.step)
      .build()
  }

  implicit val micropaymentChannelClosedMapping =
    new ProtoMapping[MicropaymentChannelClosed, msg.MicropaymentChannelClosed] {

      override def fromProtobuf(message: msg.MicropaymentChannelClosed) =
        MicropaymentChannelClosed(exchangeId = ExchangeId(message.getExchangeId))

      override def toProtobuf(obj: MicropaymentChannelClosed) =
        msg.MicropaymentChannelClosed.newBuilder()
          .setExchangeId(obj.exchangeId.value)
          .build()
    }
}
