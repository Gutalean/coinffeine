package coinffeine.peer.exchange

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor

class SingleRunDefaultExchangeActorTest extends DefaultExchangeActorTest {

  "The exchange actor" should "report an exchange success on the happy path" in new Fixture {
    startActor()
    givenMicropaymentChannelSuccess()
    notifyDepositDestination(CompletedChannel)
    walletActor.expectMsg(WalletActor.UnblockBitcoins(currentExchange.id))
    listener.expectMsgPF(hint = "successful exchange") {
      case ExchangeSuccess(ex) if ex.isSuccess =>
    }
    listener.reply(ExchangeActor.FinishExchange)
    expectMicropaymentChannelFinish()
    listener.expectTerminated(actor)
  }

  it should "forward progress reports" in new Fixture {
    startActor()
    listener.expectNoMsg()
    val progressUpdate = ExchangeUpdate(runningExchange.completeStep(1))
    micropaymentChannelActor.probe.send(actor, progressUpdate)
    listener.expectMsg(progressUpdate)
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, progressUpdate)
    listener.expectMsg(progressUpdate)
    system.stop(actor)
  }

  it should "report a failure if the handshake couldn't even start" in new Fixture {
    givenFailingUserInfoLookup()
    startActor()
    expectFailureTermination()
  }

  it should "report a failure if the handshake fails" in new Fixture {
    givenFailingHandshake()
    startActor()
    expectFailureTermination()
  }

  it should "report a failure and ask the broadcaster publish the refund if commitments are invalid" in
    new Fixture {
      givenHandshakeWillSucceedWithInvalidCounterpartCommitment()
      startActor()
      listener.expectNoMsg()
      notifyDepositDestination(DepositRefund)
      expectFailureTermination().exchange.cause shouldBe
        FailureCause.Abortion(AbortionCause.InvalidCommitments(Both(true, false)))
    }

  it should "report a failure if the actual exchange fails" in new Fixture {
    startActor()

    val error = new Error("exchange failure")
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, MicroPaymentChannelActor.ChannelFailure(1, error))

    notifyDepositDestination(ChannelAtStep(1))
    expectFailureTermination(finishMicropaymentChannel = true)
  }

  it should "report a failure if the broadcast failed" in new Fixture {
    broadcaster.givenBroadcasterWillFail()
    startActor()
    givenMicropaymentChannelSuccess()
    val result = expectFailureTermination(finishMicropaymentChannel = true)
    result.exchange.cause shouldBe FailureCause.NoBroadcast
  }

  it should "report a failure if the broadcast succeeds with an unexpected transaction" in
    new Fixture {
      val unexpectedTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      broadcaster.givenBroadcasterWillSucceed(unexpectedTx)
      startActor()
      givenMicropaymentChannelSuccess()
      notifyDepositDestination(UnexpectedDestination, unexpectedTx)
      inside(expectFailureTermination(finishMicropaymentChannel = true).exchange) {
        case FailedExchange(_, _, FailureCause.UnexpectedBroadcast, _, Some(`unexpectedTx`)) =>
      }
    }

  it should "report a failure if the broadcast is forcefully finished because it took too long" in
    new Fixture {
      val midWayTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      broadcaster.givenBroadcasterWillPanic(midWayTx)
      startActor()
      givenMicropaymentChannelCreation()
      micropaymentChannelActor.probe.send(actor, ExchangeUpdate(runningExchange.completeStep(1)))
      listener.expectMsgType[ExchangeUpdate]
      notifyDepositDestination(DepositRefund, midWayTx)
      val failedExchange = expectFailureTermination(finishMicropaymentChannel = true).exchange
      failedExchange.cause shouldBe FailureCause.PanicBlockReached
      failedExchange.transaction.value shouldBe midWayTx
      failedExchange.progress.bitcoinsTransferred.buyer shouldBe 'positive
    }

  it should "unblock funds when finishing with error" in new Fixture {
    givenFailingUserInfoLookup()
    startActor()
    expectFailureTermination()
    walletActor.expectMsg(WalletActor.UnblockBitcoins(currentExchange.id))
  }

  it should "not unblock funds when abruptly stopped" in new Fixture {
    startActor()
    expectNoMsg()
    system.stop(actor)
    walletActor.expectNoMsg()
  }
}
