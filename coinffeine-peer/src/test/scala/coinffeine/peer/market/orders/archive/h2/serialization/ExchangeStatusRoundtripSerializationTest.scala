package coinffeine.peer.market.orders.archive.h2.serialization

import scalaz.syntax.std.option._

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.AbortionCause.{HandshakeCommitmentsFailure, InvalidCommitments}
import coinffeine.model.exchange.CancellationCause._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange.ExchangeStatus
import coinffeine.model.exchange.ExchangeStatus.{Aborting, Exchanging, Failed, WaitingDepositConfirmation}
import coinffeine.model.exchange.FailureCause._
import coinffeine.model.exchange.HandshakeFailureCause.{BrokerAbortion, CannotCreateDeposits, SignatureTimeout}
import coinffeine.protocol.messages.MessageGenerators._

class ExchangeStatusRoundtripSerializationTest extends UnitTest with PropertyChecks {

  val peerInfos = for {
    accountId <- Gen.alphaStr
    bitcoinKey <- Gen.parameterized(_ => new KeyPair().publicKey)
  } yield PeerInfo(accountId, bitcoinKey)

  val waitingDepositConfirmations = for {
    user <- peerInfos
    counterpart <- peerInfos
  } yield WaitingDepositConfirmation(user, counterpart)

  val exchangingStatuses = arbitrary[Both[Hash]].map(Exchanging.apply)

  val cancellationCauses = Gen.oneOf(
    Gen.const(UserCancellation),
    Gen.const(CannotStartHandshake),
    Gen.oneOf(SignatureTimeout, BrokerAbortion, CannotCreateDeposits).map(HandshakeFailed.apply)
  )

  val abortionCauses = Gen.oneOf(
    Gen.const(HandshakeCommitmentsFailure),
    arbitrary[Both[Boolean]].filter(_.toSeq.exists(!_)).map(InvalidCommitments.apply)
  )

  val failedStatuses = for {
    cause <- Gen.oneOf(
      cancellationCauses.map(Cancellation.apply),
      abortionCauses.map(Abortion.apply),
      Gen.choose(1, 100).map(StepFailed.apply),
      Gen.oneOf(PanicBlockReached, UnexpectedBroadcast, NoBroadcast)
    )
  } yield Failed(cause)

  val exchangeStatuses = Gen.oneOf[ExchangeStatus](
    Gen.const(ExchangeStatus.Handshaking),
    waitingDepositConfirmations,
    exchangingStatuses,
    Gen.const(ExchangeStatus.Successful),
    failedStatuses,
    abortionCauses.map(Aborting.apply)
  )

  "Exchange status" should "support roundtrip serialization to string" in {
    forAll(exchangeStatuses) { (original: ExchangeStatus) =>
      val serialized = ExchangeStatusFormatter.format(original)
      val deserialized = ExchangeStatusParser.parse(serialized)
      deserialized shouldBe original.some
    }
  }
}
