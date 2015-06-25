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
import coinffeine.model.order._
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

  implicit val arbitraryBitcoinAmount: Arbitrary[BitcoinAmount] =
    Arbitrary(arbitrary[Long].map(n => Bitcoin.fromSatoshi(n.abs)))

  def fiatAmountOf(currency: FiatCurrency): Gen[FiatAmount] =
    Gen.chooseNum[Long](0, currency.unitsInOne * 10000, 1, currency.unitsInOne)
        .map(currency.fromUnits)

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

  implicit val arbitraryMarket: Arbitrary[Market] =
    Arbitrary(arbitrary[FiatCurrency].map(Market.apply))

  val commitmentNotifications: Gen[CommitmentNotification] = for {
    exchangeId <- arbitrary[ExchangeId]
    commitments <- arbitrary[Both[Hash]]
  } yield CommitmentNotification(exchangeId, commitments)

  val commitmentNotificationAcks: Gen[CommitmentNotificationAck] =
    arbitrary[ExchangeId].map(CommitmentNotificationAck.apply)

  val exchangeCommitments: Gen[ExchangeCommitment] = for  {
    id <- arbitrary[ExchangeId]
    keyPair <- arbitrary[KeyPair]
    commitment <- arbitrary[ImmutableTransaction]
  } yield ExchangeCommitment(id, keyPair.publicKey, commitment)

  val exchangeRejectionCauses: Gen[ExchangeRejection.Cause] = Gen.oneOf(
    ExchangeRejection.CounterpartTimeout,
    ExchangeRejection.InvalidOrderMatch,
    ExchangeRejection.UnavailableFunds
  )

  val exchangeRejections: Gen[ExchangeRejection] = for {
    id <- arbitrary[ExchangeId]
    reason <- exchangeRejectionCauses
  } yield ExchangeRejection(id, reason)

  val exchangeAbortions: Gen[ExchangeAborted] = for {
    id <- arbitrary[ExchangeId]
    cause <- Gen.oneOf(
      exchangeRejectionCauses.map(ExchangeAborted.Rejected.apply),
      arbitrary[Hash].map(ExchangeAborted.PublicationFailure.apply),
      arbitrary[PeerId].map(ExchangeAborted.InvalidCommitments(_)),
      Gen.const(ExchangeAborted.Timeout)
    )
  } yield ExchangeAborted(id, cause)

  val channelClosures: Gen[MicropaymentChannelClosed] =
    arbitrary[ExchangeId].map(MicropaymentChannelClosed.apply)

  def priceIn(currency: FiatCurrency): Gen[Price] = Gen.posNum[Long].map { n =>
    Price(BigDecimal(n) / 1000, currency)
  }

  def orderPriceIn(currency: FiatCurrency): Gen[OrderPrice] = Gen.oneOf(
    Gen.const(MarketPrice(currency)),
    priceIn(currency).map(price => LimitPrice(price))
  )

  def orderBookEntries(currency: FiatCurrency): Gen[OrderBookEntry] = for {
    id <- arbitrary[OrderId]
    orderType <- arbitrary[OrderType]
    amount <- arbitrary[BitcoinAmount] suchThat (_.isPositive)
    price <- orderPriceIn(currency)
  } yield OrderBookEntry(id, orderType, amount, price)

  def peerPositions(currency: FiatCurrency): Gen[PeerPositions] = for {
    positions <- Gen.containerOf[Seq, OrderBookEntry](orderBookEntries(currency))
  } yield PeerPositions(Market(currency), positions)

  val peerPositions: Gen[PeerPositions] = for {
    currency <- arbitrary[FiatCurrency]
    positions <- peerPositions(currency)
  } yield positions

  val openOrders: Gen[OpenOrders] = peerPositions.map(OpenOrders.apply)

  val openOrderRequests: Gen[OpenOrdersRequest] =
    arbitrary[Market].map(OpenOrdersRequest.apply)

  val orderMatch: Gen[OrderMatch] = for {
    orderId <- arbitrary[OrderId]
    exchangeId <- arbitrary[ExchangeId]
    bitcoinAmounts <- arbitrary[Both[BitcoinAmount]] suchThat (_.forall(_.isPositive))
    currency <- arbitrary[FiatCurrency]
    fiatAmounts <- genBoth(fiatAmountOf(currency)) suchThat (_.forall(_.isPositive))
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

  val quotes: Gen[Quote] = for {
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
