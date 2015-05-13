package coinffeine.peer.market.orders.archive.h2.serialization

import scalaz.syntax.std.option._

import coinffeine.common.test.UnitTest
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._

class ExchangeStatusParserTest extends UnitTest {

  "An exchange status parser" should "parse handshaking status" in {
    ExchangeStatusParser.parse("Handshaking") shouldBe ExchangeStatus.Handshaking.some
  }

  it should "parse waiting for deposits status" in {
    val key1 = new PublicKey().publicKey
    val key2 = new PublicKey().publicKey
    val expected = ExchangeStatus.WaitingDepositConfirmation(
      user = PeerInfo("account1", key1),
      counterpart = PeerInfo("account2", key2)
    )
    ExchangeStatusParser.parse(
      s"""WaitingDepositConfirmation(
         |  PeerInfo("account1", "${encode(key1)}"),
         |  PeerInfo("account2", "${encode(key2)}")
         |)""".stripMargin) shouldBe expected.some
  }

  it should "parse exchanging status" in {
    val tx1 = "4fcd019be69aa63c903711b033ea3800862cd68901deb9fa04215c2f198951eb"
    val tx2 = "2fcc3baadedd7c90fc4e4d40cd019c233e6b28ad15eab2e65a2f6406d5b00556"
    ExchangeStatusParser.parse(s"""Exchanging("$tx1", "$tx2")""") shouldBe
      ExchangeStatus.Exchanging(Both(new Hash(tx1), new Hash(tx2))).some
  }

  it should "parse successful status" in {
    ExchangeStatusParser.parse("Successful") shouldBe ExchangeStatus.Successful.some
  }

  it should "parse failed status because of cancellation" in {
    ExchangeStatusParser.parse("Failed(Cancellation(UserCancellation))") shouldBe
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.UserCancellation)).some
    ExchangeStatusParser.parse("Failed(Cancellation(CannotStartHandshake))") shouldBe
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.CannotStartHandshake)).some
    ExchangeStatusParser.parse("Failed(Cancellation(HandshakeFailed(BrokerAbortion)))") shouldBe
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
        HandshakeFailureCause.BrokerAbortion))).some
    ExchangeStatusParser.parse("Failed(Cancellation(HandshakeFailed(CannotCreateDeposits)))") shouldBe
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
        HandshakeFailureCause.CannotCreateDeposits))).some
    ExchangeStatusParser.parse("Failed(Cancellation(HandshakeFailed(SignatureTimeout)))") shouldBe
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
        HandshakeFailureCause.SignatureTimeout))).some
  }

  it should "parse failed status because of abortion" in {
    ExchangeStatusParser.parse("Failed(Abortion(HandshakeCommitmentsFailure))") shouldBe
      ExchangeStatus.Failed(FailureCause.Abortion(AbortionCause.HandshakeCommitmentsFailure)).some
    ExchangeStatusParser.parse("Failed(Abortion(InvalidCommitments(true, false)))") shouldBe
      ExchangeStatus.Failed(FailureCause.Abortion(AbortionCause.InvalidCommitments(
        Both(buyer = true, seller = false)))).some
  }

  it should "parse failed status because of broadcasts issues" in {
    ExchangeStatusParser.parse("Failed(PanicBlockReached)") shouldBe
      ExchangeStatus.Failed(FailureCause.PanicBlockReached).some
    ExchangeStatusParser.parse("Failed(NoBroadcast)") shouldBe
      ExchangeStatus.Failed(FailureCause.NoBroadcast).some
    ExchangeStatusParser.parse("Failed(UnexpectedBroadcast)") shouldBe
      ExchangeStatus.Failed(FailureCause.UnexpectedBroadcast).some
  }

  it should "parse failed status because of step failure" in {
    ExchangeStatusParser.parse("Failed(StepFailed(13))") shouldBe
      ExchangeStatus.Failed(FailureCause.StepFailed(13)).some
  }

  it should "parse aborting status" in {
    ExchangeStatusParser.parse("Aborting(HandshakeCommitmentsFailure)") shouldBe
      ExchangeStatus.Aborting(AbortionCause.HandshakeCommitmentsFailure).some
  }

  it should "reject invalid input" in {
    ExchangeStatusParser.parse("invalid $#% input") shouldBe 'empty
  }

  def encode(key: PublicKey): String = org.bitcoinj.core.Utils.HEX.encode(key.getPubKey)
}
