package coinffeine.peer.exchange

import scala.util.control.NoStackTrace

import akka.actor._
import akka.testkit._
import org.joda.time.DateTime
import org.scalatest.{Inside, OptionValues}

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor.{Collaborators, ExchangeFailure}
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor
import coinffeine.peer.exchange.protocol.{FakeExchangeProtocol, MicroPaymentChannel}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective

/** Base class for testing [[DefaultExchangeActor]] */
abstract class DefaultExchangeActorTest extends CoinffeineClientTest("exchange")
  with SellerPerspective with OptionValues with Inside {

  protected val dummyTx = ImmutableTransaction(new MutableTransaction(network))

  protected trait Fixture {
    protected val currentExchange = exchange.withId(ExchangeId.random())
    protected val listener, blockchain, peers, walletActor, paymentProcessor = TestProbe()
    protected val micropaymentChannelActor, depositWatcherActor = new MockSupervisedActor()
    protected val broadcaster = new MockedBroadcaster()
    private val peerInfoLookup = new PeerInfoLookupStub()
    protected val handshakeActor = new MockedHandshakeActor(DefaultExchangeActorTest.this)
    private def props = Props(new DefaultExchangeActor(
      new FakeExchangeProtocol,
      currentExchange,
      peerInfoLookup,
      new DefaultExchangeActor.Delegates {
        def transactionBroadcaster(refund: ImmutableTransaction) = broadcaster.props
        def handshake(user: PeerInfo, timestamp: DateTime, listener: ActorRef) = handshakeActor.props
        def micropaymentChannel(channel: MicroPaymentChannel,
                                resultListeners: Set[ActorRef]) =
          micropaymentChannelActor.props(channel, resultListeners)
        def depositWatcher(exchange: DepositPendingExchange,
                           myDeposit: ImmutableTransaction,
                           refundTx: ImmutableTransaction,
                           herDeposit: Option[ImmutableTransaction])(implicit context: ActorContext): Props =
          depositWatcherActor.props(exchange, myDeposit, herDeposit, refundTx)
      },
      Collaborators(walletActor.ref, paymentProcessor.ref, gateway.ref, peers.ref, blockchain.ref,
        listener.ref)
    ))
    var actor: ActorRef = _

    givenSuccessfulUserInfoLookup()
    givenHandshakeWillSucceed()

    protected def givenSuccessfulUserInfoLookup(): Unit = {
      peerInfoLookup.willSucceed(Exchange.PeerInfo("Account007", new KeyPair()))
    }

    protected def givenFailingUserInfoLookup(): Unit = {
      peerInfoLookup.willFail(new Exception("injected lookup error") with NoStackTrace)
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
      handshakeActor.givenHandshakeWillSucceed(commitments, dummyTx)
    }

    protected def givenFailingHandshake(): Unit = {
      handshakeActor.givenFailingHandshake(ExchangeTimestamps.completion)
    }

    protected def givenHandshakeWillSucceedWithInvalidCounterpartCommitment(): Unit = {
      handshakeActor.givenHandshakeWillSucceedWithInvalidCounterpartCommitment(dummyTx)
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

    protected def expectFailureTermination(
        finishMicropaymentChannel: Boolean = false): ExchangeFailure = {
      val failure = listener.expectMsgType[ExchangeFailure]
      listener.reply(ExchangeActor.FinishExchange)
      if (finishMicropaymentChannel) { expectMicropaymentChannelFinish() }
      listener.expectTerminated(actor)
      failure
    }

    protected def expectMicropaymentChannelFinish(): Unit = {
      micropaymentChannelActor.expectMsg(MicroPaymentChannelActor.Finish)
      micropaymentChannelActor.stop()
      micropaymentChannelActor.expectStop()
    }
  }
}
