package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.testkit._
import org.scalatest.{Inside, OptionValues}

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor.{Collaborators, _}
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor
import coinffeine.peer.exchange.protocol.{MicroPaymentChannel, MockExchangeProtocol}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective

class DefaultExchangeActorTest extends CoinffeineClientTest("exchange")
  with SellerPerspective with OptionValues with Inside {

  private val dummyTx = ImmutableTransaction(new MutableTransaction(network))

  "The exchange actor" should "report an exchange success on the happy path" in new Fixture {
    startActor()
    givenMicropaymentChannelSuccess()
    notifyDepositDestination(CompletedChannel)
    listener.expectMsg(ExchangeSuccess(completedExchange))
    listener.expectTerminated(actor)
  }

  it should "forward progress reports" in new Fixture {
    startActor()
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
    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure if the handshake fails" in new Fixture {
    givenFailingHandshake()
    startActor()
    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure and ask the broadcaster publish the refund if commitments are invalid" in
    new Fixture {
      givenHandshakeWillSucceedWithInvalidCounterpartCommitment()
      startActor()
      listener.expectNoMsg()
      notifyDepositDestination(DepositRefund)
      inside (listener.expectMsgType[ExchangeFailure].exchange.state.cause) {
        case Exchange.Abortion(Exchange.InvalidCommitments(Both(Failure(_), Success(_)))) =>
      }
      listener.expectTerminated(actor)
    }

  it should "report a failure if the actual exchange fails" in new Fixture {
    startActor()

    val error = new Error("exchange failure")
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, MicroPaymentChannelActor.ChannelFailure(1, error))

    notifyDepositDestination(ChannelAtStep(1))

    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure if the broadcast failed" in new Fixture {
    givenBroadcasterWillFail()
    startActor()
    givenMicropaymentChannelSuccess()
    listener.expectMsgType[ExchangeFailure].exchange.state.cause shouldBe Exchange.NoBroadcast
    listener.expectTerminated(actor)
  }

  it should "report a failure if the broadcast succeeds with an unexpected transaction" in
    new Fixture {
      val unexpectedTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      givenBroadcasterWillSucceed(unexpectedTx)
      startActor()
      givenMicropaymentChannelSuccess()
      notifyDepositDestination(UnexpectedDestination, unexpectedTx)
      inside(listener.expectMsgType[ExchangeFailure].exchange.state) {
        case Exchange.Failed(Exchange.UnexpectedBroadcast, _, _, Some(`unexpectedTx`)) =>
      }
      listener.expectTerminated(actor)
    }

  it should "report a failure if the broadcast is forcefully finished because it took too long" in
    new Fixture {
      val midWayTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      givenBroadcasterWillPanic(midWayTx)
      startActor()
      givenMicropaymentChannelCreation()
      notifyDepositDestination(DepositRefund, midWayTx)
      val failedState = listener.expectMsgType[ExchangeFailure].exchange.state
      failedState.cause shouldBe Exchange.PanicBlockReached
      failedState.transaction.value shouldBe midWayTx
      listener.expectTerminated(actor)
    }

  it should "unblock funds on termination" in new Fixture {
    startActor()
    system.stop(actor)
    walletActor.expectMsg(WalletActor.UnblockBitcoins(exchangeId))
  }

  trait Fixture {
    protected val listener, blockchain, peers, walletActor, paymentProcessor = TestProbe()
    protected val micropaymentChannelActor, depositWatcherActor = new MockSupervisedActor()
    private val peerInfoLookup = new PeerInfoLookupStub()
    private var handshakeProps, broadcasterProps: Props = _
    private def props = Props(new DefaultExchangeActor(
      new MockExchangeProtocol,
      exchange,
      peerInfoLookup,
      new DefaultExchangeActor.Delegates {
        def transactionBroadcaster(refund: ImmutableTransaction)(implicit context: ActorContext) =
          broadcasterProps
        def handshake(user: PeerInfo, listener: ActorRef) = handshakeProps
        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]) = micropaymentChannelActor.props
        def depositWatcher(exchange: HandshakingExchange[_ <: FiatCurrency],
                           deposit: ImmutableTransaction,
                           refundTx: ImmutableTransaction)(implicit context: ActorContext): Props =
          depositWatcherActor.props
      },
      Collaborators(walletActor.ref, paymentProcessor.ref, gateway.ref, peers.ref, blockchain.ref,
        listener.ref)
    ))
    var actor: ActorRef = _

    givenSuccessfulUserInfoLookup()
    givenHandshakeWillSucceed()
    givenBroadcasterWillSucceed()

    protected def givenSuccessfulUserInfoLookup(): Unit = {
      peerInfoLookup.willSucceed(Exchange.PeerInfo("Account007", new KeyPair()))
    }

    protected def givenFailingUserInfoLookup(): Unit = {
      peerInfoLookup.willFail(new Exception("injected error"))
    }

    protected def startActor(): Unit = {
      actor = system.actorOf(props)
      listener.watch(actor)
    }

    protected def restartActor(): Unit = {
      system.stop(actor)
      listener.expectTerminated(actor)
      startActor()
    }

    protected def givenHandshakeWillSucceed(
        commitments: Both[ImmutableTransaction] = Both.fill(dummyTx)): Unit = {
      handshakeProps = HandshakeStub.successful(commitments)
    }

    protected def givenFailingHandshake(): Unit = {
      handshakeProps = HandshakeStub.failure
    }

    protected def givenHandshakeWillSucceedWithInvalidCounterpartCommitment(): Unit = {
      val commitments = Both(buyer = MockExchangeProtocol.InvalidDeposit, seller = dummyTx)
      givenHandshakeWillSucceed(commitments)
    }

    protected def givenBroadcasterWillSucceed(tx: ImmutableTransaction = dummyTx): Unit = {
      broadcasterProps = BroadcasterStub.broadcasting(tx)
    }

    protected def givenBroadcasterWillPanic(tx: ImmutableTransaction): Unit = {
      broadcasterProps = BroadcasterStub.broadcasting(tx, timeout = 1.second.dilated)
    }

    protected def givenBroadcasterWillFail(): Unit = {
      broadcasterProps = BroadcasterStub.failing
    }

    protected def notifyDepositDestination(destination: DepositDestination = CompletedChannel,
                                 tx: ImmutableTransaction = dummyTx): Unit = {
      depositWatcherActor.probe.send(actor, DepositWatcher.DepositSpent(tx, destination))
    }

    protected def givenMicropaymentChannelCreation(): Unit = {
      micropaymentChannelActor.expectCreation()
    }

    protected def givenMicropaymentChannelSuccess(): Unit = {
      givenMicropaymentChannelCreation()
      micropaymentChannelActor.probe
        .send(actor, MicroPaymentChannelActor.ChannelSuccess(Some(dummyTx)))
    }

    protected def shouldWatchForTheTransactions(): Unit = {
      val deposits = Both(
        buyer = new Hash(List.fill(64)("0").mkString),
        seller = new Hash(List.fill(64)("1").mkString)
      )
      blockchain.expectMsgAllOf(
        BlockchainActor.WatchPublicKey(counterpart.bitcoinKey),
        BlockchainActor.WatchPublicKey(user.bitcoinKey))
      blockchain.expectMsgAllOf(
        BlockchainActor.RetrieveTransaction(deposits.buyer),
        BlockchainActor.RetrieveTransaction(deposits.seller)
      )
    }
  }

  private class HandshakeStub(result: HandshakeResult) extends Actor {
    override def preStart(): Unit = {
      context.parent ! result
    }
    override val receive: Receive = Map.empty
  }

  private object HandshakeStub {
    def successful(commitments: Both[ImmutableTransaction]) =
      Props(new HandshakeStub(HandshakeSuccess(
        exchange = exchange.startHandshaking(user, counterpart),
        bothCommitments = commitments,
        refundTx = dummyTx
      )))

    def failure = Props(new HandshakeStub(HandshakeFailure(new Exception("injected failure"))))
  }
}
