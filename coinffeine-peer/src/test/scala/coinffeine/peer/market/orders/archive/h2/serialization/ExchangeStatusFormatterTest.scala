package coinffeine.peer.market.orders.archive.h2.serialization

import coinffeine.common.test.UnitTest
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.model.{Both, bitcoin}

class ExchangeStatusFormatterTest extends UnitTest {

  "An exchange status formatter" should "format handshaking status" in {
    ExchangeStatusFormatter.format(ExchangeStatus.Handshaking) shouldBe "Handshaking"
  }

  it should "format waiting for deposits status" in {
    val key1 = new PublicKey().publicKey
    val key2 = new PublicKey().publicKey
    val status = ExchangeStatus.WaitingDepositConfirmation(
      user = PeerInfo("account1", key1),
      counterpart = PeerInfo("account2", key2)
    )
    ExchangeStatusFormatter.format(status) shouldBe
      s"""WaitingDepositConfirmation(
         |  PeerInfo("account1", "${encode(key1)}"),
         |  PeerInfo("account2", "${encode(key2)}")
         |)""".stripMargin.replaceAll("\\s", "")
  }

  it should "format exchanging status" in {
    val tx1 = "4fcd019be69aa63c903711b033ea3800862cd68901deb9fa04215c2f198951eb"
    val tx2 = "2fcc3baadedd7c90fc4e4d40cd019c233e6b28ad15eab2e65a2f6406d5b00556"
    val state = ExchangeStatus.Exchanging(Both(new bitcoin.Hash(tx1), new bitcoin.Hash(tx2)))
    ExchangeStatusFormatter.format(state) shouldBe s"""Exchanging("$tx1","$tx2")"""
  }

  it should "format successful status" in {
    ExchangeStatusFormatter.format(ExchangeStatus.Successful) shouldBe "Successful"
  }

  it should "format failed status because of cancellation" in {
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.UserCancellation))) shouldBe
      "Failed(Cancellation(UserCancellation))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.CannotStartHandshake))) shouldBe
      "Failed(Cancellation(CannotStartHandshake))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
      HandshakeFailureCause.BrokerAbortion)))) shouldBe
      "Failed(Cancellation(HandshakeFailed(BrokerAbortion)))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
      HandshakeFailureCause.CannotCreateDeposits)))) shouldBe
      "Failed(Cancellation(HandshakeFailed(CannotCreateDeposits)))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
      HandshakeFailureCause.SignatureTimeout)))) shouldBe
      "Failed(Cancellation(HandshakeFailed(SignatureTimeout)))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Cancellation(CancellationCause.HandshakeFailed(
      HandshakeFailureCause.InvalidCounterpartAccountId)))) shouldBe
      "Failed(Cancellation(HandshakeFailed(InvalidCounterpartAccountId)))"
  }

  it should "format failed status because of abortion" in {
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Abortion(AbortionCause.HandshakeCommitmentsFailure))) shouldBe
      "Failed(Abortion(HandshakeCommitmentsFailure))"
    ExchangeStatusFormatter.format(
      ExchangeStatus.Failed(FailureCause.Abortion(AbortionCause.InvalidCommitments(
      Both(buyer = true, seller = false))))) shouldBe
      "Failed(Abortion(InvalidCommitments(true,false)))"
  }

  it should "format failed status because of broadcasts issues" in {
    ExchangeStatusFormatter.format(ExchangeStatus.Failed(FailureCause.PanicBlockReached)) shouldBe
      "Failed(PanicBlockReached)"
    ExchangeStatusFormatter.format(ExchangeStatus.Failed(FailureCause.NoBroadcast)) shouldBe
      "Failed(NoBroadcast)"
    ExchangeStatusFormatter.format(ExchangeStatus.Failed(FailureCause.UnexpectedBroadcast)) shouldBe
      "Failed(UnexpectedBroadcast)"
  }

  it should "format failed status because of failed step" in {
    ExchangeStatusFormatter.format(ExchangeStatus.Failed(FailureCause.StepFailed(3))) shouldBe
      "Failed(StepFailed(3))"
  }

  it should "format aborting status" in {
    ExchangeStatusFormatter.format(
      ExchangeStatus.Aborting(AbortionCause.HandshakeCommitmentsFailure)) shouldBe
      "Aborting(HandshakeCommitmentsFailure)"
  }

  def encode(key: PublicKey): String = org.bitcoinj.core.Utils.HEX.encode(key.getPubKey)
}
