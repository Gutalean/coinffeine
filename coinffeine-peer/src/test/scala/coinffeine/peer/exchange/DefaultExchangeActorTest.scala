package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor._
import akka.testkit._
import org.joda.time.DateTime
import org.scalatest.{Inside, OptionValues}

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.model.currency._
import coinffeine.model.exchange.Exchange.PeerInfo
import coinffeine.model.exchange._
import coinffeine.peer.bitcoin.blockchain.BlockchainActor
import coinffeine.peer.exchange.DepositWatcher._
import coinffeine.peer.exchange.ExchangeActor.Collaborators
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.micropayment.MicroPaymentChannelActor
import coinffeine.peer.exchange.protocol.{MicroPaymentChannel, MockExchangeProtocol}
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
    private val peerInfoLookup = new PeerInfoLookupStub()
    private var handshakeProps, broadcasterProps: Props = _
    private def props = Props(new DefaultExchangeActor(
      new MockExchangeProtocol,
      currentExchange,
      peerInfoLookup,
      new DefaultExchangeActor.Delegates {
        def transactionBroadcaster(refund: ImmutableTransaction)(implicit context: ActorContext) =
          broadcasterProps
        def handshake(user: PeerInfo, timestamp: DateTime, listener: ActorRef) = handshakeProps
        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]) =
          micropaymentChannelActor.props(channel, resultListeners)
        def depositWatcher(exchange: DepositPendingExchange[_ <: FiatCurrency],
                           deposit: ImmutableTransaction,
                           refundTx: ImmutableTransaction)(implicit context: ActorContext): Props =
          depositWatcherActor.props(exchange, deposit, refundTx)
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
    def successful(commitments: Both[ImmutableTransaction]) = {
      val handshakeSuccess = HandshakeSuccess(
        exchange = exchange.handshake(user, counterpart, ExchangeTimestamps.handshakingStart),
        bothCommitments = commitments,
        refundTx = dummyTx,
        timestamp = ExchangeTimestamps.completion
      )
      Props(new HandshakeStub(handshakeSuccess))
    }

    def failure = {
      val exception = new scala.Exception("injected handshake failure") with NoStackTrace
      Props(new HandshakeStub(HandshakeFailure(exception, ExchangeTimestamps.completion)))
    }
  }
}
