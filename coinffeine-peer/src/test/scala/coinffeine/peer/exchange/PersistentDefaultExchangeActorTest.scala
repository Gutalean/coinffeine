package coinffeine.peer.exchange

import coinffeine.model.exchange._
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor.ChannelFailure

class PersistentDefaultExchangeActorTest extends DefaultExchangeActorTest {

  "The exchange actor" should "remember the happy path result" in new Fixture {
    startActor()
    givenMicropaymentChannelSuccess()
    notifyDepositDestination(CompletedChannel)
    val originalSuccess = listener.expectMsgPF(hint = "a completed exchange") {
      case result: ExchangeSuccess if result.exchange.id == exchange.id => result
    }

    restartActor()
    listener.expectMsg(originalSuccess)
    listener.reply(ExchangeActor.FinishExchange)
    listener.expectTerminated(actor)
  }

  it should "remember it failed because user info was impossible to retrieve" in new Fixture {
    givenFailingUserInfoLookup()
    startActor()
    listener.expectMsgType[ExchangeFailure]

    givenSuccessfulUserInfoLookup()
    restartActor()
    inside (expectFailureTermination().exchange.cause) {
      case FailureCause.Cancellation(CancellationCause.CannotStartHandshake) =>
    }
  }

  it should "remember that the handshake failed" in new Fixture {
    givenFailingHandshake()
    startActor()
    listener.expectMsgType[ExchangeFailure]

    restartActor()
    inside (expectFailureTermination().exchange.cause) {
      case FailureCause.Cancellation(CancellationCause.HandshakeFailed(_)) =>
    }
  }

  it should "remember that the actual exchange failed" in new Fixture {
    startActor()
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, ChannelFailure(1, new Error("exchange failure")))
    notifyDepositDestination(ChannelAtStep(1))
    listener.expectMsgType[ExchangeFailure]

    restartActor()
    inside(expectFailureTermination().exchange.cause) {
      case FailureCause.StepFailed(1) =>
    }
  }

  it should "remember that publication failed" in new Fixture {
    givenBroadcasterWillFail()
    startActor()
    givenMicropaymentChannelSuccess()
    listener.expectMsgType[ExchangeFailure]

    restartActor()
    expectFailureTermination().exchange.cause shouldBe FailureCause.NoBroadcast
  }

  it should "remember that it panicked publishing the best available transaction" in new Fixture {
    givenBroadcasterWillPanic(dummyTx)
    startActor()
    givenMicropaymentChannelCreation()
    notifyDepositDestination(ChannelAtStep(3))
    listener.expectMsgType[ExchangeFailure]

    restartActor()
    expectFailureTermination().exchange.cause shouldBe FailureCause.PanicBlockReached
  }

  // TODO: brainwhashed after FinishExchange
}
