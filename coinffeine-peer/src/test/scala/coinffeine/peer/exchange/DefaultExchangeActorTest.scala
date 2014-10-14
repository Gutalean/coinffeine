package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, OptionValues}

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor.{Collaborators, _}
import coinffeine.peer.exchange.TransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor.{HandshakeFailure, HandshakeSuccess}
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor
import coinffeine.peer.exchange.protocol.{MicroPaymentChannel, MockExchangeProtocol}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.peer.payment.MockPaymentProcessorFactory

class DefaultExchangeActorTest extends CoinffeineClientTest("buyerExchange")
  with SellerPerspective with Eventually with OptionValues with Inside {

  implicit def testTimeout = new Timeout(5 second)

  private val deposits = Both(
    buyer = new Hash(List.fill(64)("0").mkString),
    seller = new Hash(List.fill(64)("1").mkString)
  )
  private val dummyTx = ImmutableTransaction(new MutableTransaction(network))
  private val dummyPaymentProcessor = system.actorOf(
    new MockPaymentProcessorFactory(List.empty)
      .newProcessor(fiatAddress = "", initialBalance = Seq.empty)
  )

  trait Fixture {
    val listener, blockchain, peers, walletActor = TestProbe()
    val handshakeActor, micropaymentChannelActor, transactionBroadcastActor, depositWatcherActor =
      new MockSupervisedActor()
    val peerInfoLookup = new PeerInfoLookupStub()
    private val props = Props(new DefaultExchangeActor(
      new MockExchangeProtocol,
      exchange,
      peerInfoLookup,
      new DefaultExchangeActor.Delegates {
        val transactionBroadcaster = transactionBroadcastActor.props
        def handshake(user: PeerInfo, listener: ActorRef) = handshakeActor.props
        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]) = micropaymentChannelActor.props
        def depositWatcher(exchange: HandshakingExchange[_ <: FiatCurrency],
                           deposit: ImmutableTransaction,
                           refundTx: ImmutableTransaction)(implicit context: ActorContext): Props =
          depositWatcherActor.props
      },
      Collaborators(walletActor.ref, dummyPaymentProcessor, gateway.ref, peers.ref, blockchain.ref,
        listener.ref)
    ))
    var actor: ActorRef = _

    def givenSuccessfulExchangeStart(): Unit = {
      peerInfoLookup.willSucceed(Exchange.PeerInfo("Account007", new KeyPair()))
      startActor()
      transactionBroadcastActor.expectCreation()
    }

    def startActor(): Unit = {
      actor = system.actorOf(props)
      listener.watch(actor)
    }

    def givenHandshakeSuccess(): Unit = {
      handshakeActor.expectCreation()
      handshakeActor.probe.send(actor,
        HandshakeSuccess(handshakingExchange, Both.fill(dummyTx), dummyTx))
      transactionBroadcastActor.expectMsg(StartBroadcastHandling(dummyTx, Set(actor)))
    }

    def givenHandshakeSuccessWithInvalidCounterpartCommitment(): Unit = {
      handshakeActor.expectCreation()
      val invalidCommitment = Both(buyer = MockExchangeProtocol.InvalidDeposit, seller = dummyTx)
      val handshakeSuccess = HandshakeSuccess(handshakingExchange, invalidCommitment,dummyTx)
      handshakeActor.probe.send(actor,handshakeSuccess)
      transactionBroadcastActor.expectMsg(StartBroadcastHandling(dummyTx, Set(actor)))
    }

    def givenTransactionIsCorrectlyBroadcast(destination: DepositDestination = CompletedChannel): Unit = {
      transactionBroadcastActor.expectAskWithReply {
        case PublishBestTransaction => SuccessfulBroadcast(TransactionPublished(dummyTx, dummyTx))
      }
      depositWatcherActor.probe.send(actor, DepositWatcher.DepositSpent(dummyTx, destination))
    }

    def givenMicropaymentChannelCreation(): Unit = {
      micropaymentChannelActor.expectCreation()
    }

    def givenMicropaymentChannelSuccess(): Unit = {
      givenMicropaymentChannelCreation()
      micropaymentChannelActor.probe
        .send(actor, MicroPaymentChannelActor.ChannelSuccess(Some(dummyTx)))
    }

    def shouldWatchForTheTransactions(): Unit = {
      blockchain.expectMsgAllOf(
        BlockchainActor.WatchPublicKey(counterpart.bitcoinKey),
        BlockchainActor.WatchPublicKey(user.bitcoinKey))
      blockchain.expectMsgAllOf(
        BlockchainActor.RetrieveTransaction(deposits.buyer),
        BlockchainActor.RetrieveTransaction(deposits.seller)
      )
    }
  }

  "The exchange actor" should "report an exchange success when handshake, exchange and broadcast work" in
    new Fixture {
      givenSuccessfulExchangeStart()
      givenHandshakeSuccess()
      givenMicropaymentChannelSuccess()
      givenTransactionIsCorrectlyBroadcast()
      listener.expectMsg(ExchangeSuccess(completedExchange))
      listener.expectMsgClass(classOf[Terminated])
      system.stop(actor)
    }

  it should "forward progress reports" in new Fixture {
    givenSuccessfulExchangeStart()
    val progressUpdate = ExchangeUpdate(runningExchange.increaseProgress(Both.fill(1.BTC)))
    givenHandshakeSuccess()
    micropaymentChannelActor.probe.send(actor, progressUpdate)
    listener.expectMsg(progressUpdate)
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, progressUpdate)
    listener.expectMsg(progressUpdate)
    system.stop(actor)
  }

  it should "report a failure if the handshake couldn't even start" in new Fixture {
    peerInfoLookup.willFail(new Exception("injected error"))
    startActor()
    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure if the handshake fails" in new Fixture {
    givenSuccessfulExchangeStart()
    val error = new Error("Handshake error")
    handshakeActor.expectCreation()
    handshakeActor.probe.send(actor, HandshakeFailure(error))
    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure and ask the broadcaster publish the refund if commitments are invalid" in
    new Fixture {
      givenSuccessfulExchangeStart()
      givenHandshakeSuccessWithInvalidCounterpartCommitment()
      givenTransactionIsCorrectlyBroadcast(DepositRefund)
      inside (listener.expectMsgType[ExchangeFailure].exchange.state.cause) {
        case Exchange.Abortion(Exchange.InvalidCommitments(Both(Failure(_), Success(_)))) =>
      }
      listener.expectTerminated(actor)
    }

  it should "report a failure if the actual exchange fails" in new Fixture {
    givenSuccessfulExchangeStart()
    givenHandshakeSuccess()

    val error = new Error("exchange failure")
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, MicroPaymentChannelActor.ChannelFailure(1, error))

    givenTransactionIsCorrectlyBroadcast(ChannelAtStep(1))

    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
  }

  it should "report a failure if the broadcast failed" in new Fixture {
    givenSuccessfulExchangeStart()
    givenHandshakeSuccess()
    givenMicropaymentChannelSuccess()
    val broadcastError = new Error("failed to broadcast")
    transactionBroadcastActor.expectAskWithReply {
      case PublishBestTransaction => FailedBroadcast(broadcastError)
    }
    listener.expectMsgType[ExchangeFailure].exchange.state.cause shouldBe Exchange.NoBroadcast
    listener.expectTerminated(actor)
  }

  it should "report a failure if the broadcast succeeds with an unexpected transaction" in
    new Fixture {
      givenSuccessfulExchangeStart()
      givenHandshakeSuccess()
      givenMicropaymentChannelSuccess()
      val unexpectedTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      transactionBroadcastActor.expectAskWithReply {
        case PublishBestTransaction =>
          SuccessfulBroadcast(TransactionPublished(unexpectedTx, unexpectedTx))
      }
      depositWatcherActor.probe.send(actor,
        DepositWatcher.DepositSpent(unexpectedTx, UnexpectedDestination))
      inside(listener.expectMsgType[ExchangeFailure].exchange.state) {
        case Exchange.Failed(Exchange.UnexpectedBroadcast, _, _, Some(`unexpectedTx`)) =>
      }
      listener.expectTerminated(actor)
    }

  it should "report a failure if the broadcast is forcefully finished because it took too long" in
    new Fixture {
      givenSuccessfulExchangeStart()
      givenHandshakeSuccess()
      givenMicropaymentChannelCreation()
      val midWayTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      transactionBroadcastActor.expectNoMsg()
      transactionBroadcastActor.probe
        .send(actor, SuccessfulBroadcast(TransactionPublished(midWayTx, midWayTx)))
      depositWatcherActor.probe.send(actor, DepositWatcher.DepositSpent(midWayTx, DepositRefund))
      val failedState = listener.expectMsgType[ExchangeFailure].exchange.state
      failedState.cause shouldBe Exchange.PanicBlockReached
      failedState.transaction.value shouldBe midWayTx
      listener.expectTerminated(actor)
    }

  it should "unblock funds on termination" in new Fixture {
    givenSuccessfulExchangeStart()
    system.stop(actor)
    walletActor.expectMsg(WalletActor.UnblockBitcoins(exchangeId))
  }
}
