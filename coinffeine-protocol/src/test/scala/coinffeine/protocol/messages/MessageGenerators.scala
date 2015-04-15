package coinffeine.protocol.messages

import org.bitcoinj.core.Transaction.SigHash
import org.bitcoinj.script.ScriptBuilder
import org.scalacheck.Arbitrary._
import org.scalacheck.{Arbitrary, Gen}

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market._
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.arbitration._
import coinffeine.protocol.messages.brokerage._
import coinffeine.protocol.messages.exchange._
import coinffeine.protocol.messages.handshake._

trait MessageGenerators {

  def genBoth[T](gen: Gen[T]): Gen[Both[T]] = for {
    buyer <- gen
    seller <- gen
  } yield Both(buyer, seller)

  implicit def arbitraryBoth[T: Arbitrary]: Arbitrary[Both[T]] = Arbitrary(genBoth(arbitrary[T]))

  implicit val arbitraryPeerId: Arbitrary[PeerId] = Arbitrary(Gen.identifier.map(PeerId.hashOf))

  implicit val arbitraryOrderId: Arbitrary[OrderId] =
    Arbitrary(Gen.identifier.map(OrderId.apply))

  implicit val arbitraryExchangeId: Arbitrary[ExchangeId] =
    Arbitrary(Gen.identifier.map(ExchangeId.apply))

  implicit val arbitraryOrderType: Arbitrary[OrderType] = Arbitrary(Gen.oneOf(Bid, Ask))

  implicit val arbitraryHash: Arbitrary[Hash] =
    Arbitrary(Gen.containerOfN[Array, Byte](32, arbitrary[Byte]).map(bytes => new Hash(bytes)))

  implicit val arbitraryKeyPair: Arbitrary[KeyPair] = Arbitrary(Gen.parameterized(_ => new KeyPair))

  implicit val arbitraryBitcoinAmount: Arbitrary[Bitcoin.Amount] =
    Arbitrary(arbitrary[Long].map(n => Bitcoin.fromSatoshi(n.abs)))

  def amountOf[C <: Currency](currency: C): Gen[CurrencyAmount[C]] =
    Gen.chooseNum[Long](0, currency.UnitsInOne * 10000, 1, currency.UnitsInOne).map { units =>
      CurrencyAmount(units, currency)
    }

  implicit val arbitraryImmutableTransaction: Arbitrary[ImmutableTransaction] =
    Arbitrary(for {
      lockTime <- arbitrary[Long]
      spentOutputHash <- arbitrary[Hash]
      keyPair <- arbitrary[KeyPair]
    } yield {
      val tx = new MutableTransaction(CoinffeineUnitTestNetwork)
      tx.setLockTime(lockTime)
      val script = ScriptBuilder.createOutputScript(keyPair)
      tx.addInput(spentOutputHash, 0, script)
      tx.calculateSignature(0, keyPair, script, SigHash.ALL, false)
      ImmutableTransaction(tx)
    })

  implicit val arbitraryFiatCurrency: Arbitrary[FiatCurrency] =
    Arbitrary(Gen.oneOf(Euro, UsDollar))

  implicit val arbitraryMarket: Arbitrary[Market[FiatCurrency]] =
    Arbitrary(arbitrary[FiatCurrency].map(Market.apply))

  val commitmentNotifications: Gen[CommitmentNotification] = for {
    exchangeId <- arbitrary[ExchangeId]
    commitments <- arbitrary[Both[Hash]]
  } yield CommitmentNotification(exchangeId, commitments)

  val commitmentNotificationAcks: Gen[CommitmentNotificationAck] =
    arbitrary[ExchangeId].map(CommitmentNotificationAck.apply)

  val exchangeAbortions: Gen[ExchangeAborted] = for {
    id <- arbitrary[ExchangeId]
    message <- Gen.oneOf("timeout", "rejected by counterpart", "invalid commitments")
  } yield ExchangeAborted(id, message)

  val exchangeCommitments: Gen[ExchangeCommitment] = for  {
    id <- arbitrary[ExchangeId]
    keyPair <- arbitrary[KeyPair]
    commitment <- arbitrary[ImmutableTransaction]
  } yield ExchangeCommitment(id, keyPair.publicKey, commitment)

  val exchangeRejections: Gen[ExchangeRejection] = for {
    id <- arbitrary[ExchangeId]
    reason <- Gen.oneOf("rejected by peer", "invalid exchange")
  } yield ExchangeRejection(id, reason)

  val channelClosures: Gen[MicropaymentChannelClosed] =
    arbitrary[ExchangeId].map(MicropaymentChannelClosed.apply)

  def priceIn[C <: FiatCurrency](currency: C): Gen[Price[C]] = Gen.posNum[Long].map { n =>
    Price(BigDecimal(n) / 1000, currency)
  }

  def orderPriceIn[C <: FiatCurrency](currency: C): Gen[OrderPrice[C]] = Gen.oneOf(
    Gen.const(MarketPrice(currency)),
    priceIn(currency).map(price => LimitPrice(price))
  )

  def orderBookEntries[C <: FiatCurrency](currency: C): Gen[OrderBookEntry[C]] = for {
    id <- arbitrary[OrderId]
    orderType <- arbitrary[OrderType]
    amount <- arbitrary[Bitcoin.Amount] suchThat (_.isPositive)
    price <- orderPriceIn(currency)
  } yield OrderBookEntry(id, orderType, amount, price)

  def peerPositions[C <: FiatCurrency](currency: C): Gen[PeerPositions[C]] = for {
    positions <- Gen.containerOf[Seq, OrderBookEntry[C]](orderBookEntries(currency))
  } yield PeerPositions(Market(currency), positions)

  val peerPositions: Gen[PeerPositions[FiatCurrency]] = for {
    currency <- arbitrary[FiatCurrency]
    positions <- peerPositions(currency)
  } yield positions

  val openOrders: Gen[OpenOrders[FiatCurrency]] = peerPositions.map(OpenOrders.apply)

  val openOrderRequests: Gen[OpenOrdersRequest] =
    arbitrary[Market[FiatCurrency]].map(OpenOrdersRequest.apply)

  val orderMatch: Gen[OrderMatch[FiatCurrency]] = for {
    orderId <- arbitrary[OrderId]
    exchangeId <- arbitrary[ExchangeId]
    bitcoinAmounts <- arbitrary[Both[Bitcoin.Amount]] suchThat (_.forall(_.isPositive))
    currency <- arbitrary[FiatCurrency]
    fiatAmounts <- genBoth(amountOf(currency)) suchThat (_.forall(_.isPositive))
    lockTime <- arbitrary[Long]
    counterpart <- arbitrary[PeerId]
  } yield OrderMatch(orderId, exchangeId, bitcoinAmounts, fiatAmounts, lockTime.abs, counterpart)

  val paymentProofs: Gen[PaymentProof] = for {
    exchangeId <- arbitrary[ExchangeId]
    paymentId <- arbitrary[String]
    step <- Gen.chooseNum(1, 10)
  } yield PaymentProof(exchangeId, paymentId, step)

  val peerHandshakes: Gen[PeerHandshake] = for {
    id <- arbitrary[ExchangeId]
    keyPair <- arbitrary[KeyPair]
    account <- Gen.identifier
  } yield PeerHandshake(id, keyPair.publicKey, account)

  val peerPositionsReceives: Gen[PeerPositionsReceived] =
    arbitrary[String].map(PeerPositionsReceived.apply)

  val quotes: Gen[Quote[FiatCurrency]] = for {
    currency <- arbitrary[FiatCurrency]
    highestBid <- Gen.option(priceIn(currency))
    lowestAsk <- Gen.option(priceIn(currency))
    lastPrice <- Gen.option(priceIn(currency))
  } yield Quote(Market(currency), Spread(highestBid, lowestAsk), lastPrice)

  val quoteRequests: Gen[QuoteRequest] =
    arbitrary[FiatCurrency].map(currency => QuoteRequest(Market(currency)))

  val refundSignatureRequests: Gen[RefundSignatureRequest] = for {
    id <- arbitrary[ExchangeId]
    tx <- arbitrary[ImmutableTransaction]
  } yield RefundSignatureRequest(id, tx)

  implicit val arbitrarySignature: Arbitrary[TransactionSignature] = Arbitrary(for {
    keyPair <- arbitrary[KeyPair]
    txHash <- arbitrary[Hash]
    anyoneCanPay <- arbitrary[Boolean]
    flags <- Gen.oneOf(SigHash.ALL, SigHash.NONE, SigHash.SINGLE)
  } yield new TransactionSignature(keyPair.sign(txHash), flags, anyoneCanPay))

  val refundSignatureResponse: Gen[RefundSignatureResponse] = for {
    id <- arbitrary[ExchangeId]
    signature <- arbitrary[TransactionSignature]
  } yield RefundSignatureResponse(id, signature)

  val stepSignatures: Gen[StepSignatures] = for {
    id <- arbitrary[ExchangeId]
    step <- Gen.chooseNum(1, 10)
    signatures <- arbitrary[Both[TransactionSignature]]
  } yield StepSignatures(id, step, signatures)

  implicit val arbitraryPublicMessage: Arbitrary[PublicMessage] = Arbitrary(Gen.oneOf(
    commitmentNotifications,
    commitmentNotificationAcks,
    exchangeAbortions,
    exchangeCommitments,
    exchangeRejections,
    channelClosures,
    openOrders,
    openOrderRequests,
    orderMatch,
    paymentProofs,
    peerHandshakes,
    peerPositions,
    peerPositionsReceives,
    quotes,
    quoteRequests,
    refundSignatureRequests,
    refundSignatureResponse,
    stepSignatures
  ))

  def publicMessagesNotContaining(fieldValues: Any*): Gen[PublicMessage] =
    arbitrary[PublicMessage].suchThat { message =>
      val product = message.asInstanceOf[Product]
      fieldValues.forall { fieldValue =>
        !product.productIterator.contains(fieldValue)
      }
    }
}

object MessageGenerators extends MessageGenerators
