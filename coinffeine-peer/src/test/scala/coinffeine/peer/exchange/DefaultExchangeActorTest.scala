package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor.{ActorRef, Props, Terminated}
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, OptionValues}

import coinffeine.common.akka.test.MockSupervisedActor
import coinffeine.model.bitcoin._
import coinffeine.model.currency.FiatCurrency
import coinffeine.model.currency.Implicits._
import coinffeine.model.exchange.{Both, Exchange}
import coinffeine.peer.bitcoin.BitcoinPeerActor._
import coinffeine.peer.bitcoin.BlockchainActor._
import coinffeine.peer.exchange.ExchangeActor._
import coinffeine.peer.exchange.TransactionBroadcastActor.{UnexpectedTxBroadcast => _, _}
import coinffeine.peer.exchange.handshake.HandshakeActor
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
    val handshakeActor, micropaymentChannelActor, transactionBroadcastActor = new MockSupervisedActor()
    val actor = system.actorOf(Props(new DefaultExchangeActor(
      new MockExchangeProtocol,
      ExchangeToStart(exchange, user),
      new DefaultExchangeActor.Delegates {
        val transactionBroadcaster = transactionBroadcastActor.props
        def handshake(exchange: ExchangeToStart[_ <: FiatCurrency],
                      collaborators: HandshakeActor.Collaborators) = handshakeActor.props
        def micropaymentChannel(channel: MicroPaymentChannel[_ <: FiatCurrency],
                                resultListeners: Set[ActorRef]) = micropaymentChannelActor.props
      },
      Collaborators(walletActor.ref, dummyPaymentProcessor, gateway.ref, peers.ref, listener.ref)
    )))
    listener.watch(actor)

    def startExchange(): Unit = {
      peers.expectMsg(RetrieveBlockchainActor)
      peers.reply(BlockchainActorRef(blockchain.ref))
      transactionBroadcastActor.expectCreation()
    }

    def givenHandshakeSuccess(): Unit = {
      handshakeActor.expectCreation()
      handshakeActor.probe.send(actor,
        HandshakeSuccess(handshakingExchange, Both.fill(dummyTx), dummyTx))
      transactionBroadcastActor.expectMsg(StartBroadcastHandling(dummyTx, peers.ref, Set(actor)))
    }

    def givenHandshakeSuccessWithInvalidCounterpartCommitment(): Unit = {
      handshakeActor.expectCreation()
      val invalidCommitment = Both(buyer = MockExchangeProtocol.InvalidDeposit, seller = dummyTx)
      val handshakeSuccess = HandshakeSuccess(handshakingExchange, invalidCommitment,dummyTx)
      handshakeActor.probe.send(actor,handshakeSuccess)
      transactionBroadcastActor.expectMsg(StartBroadcastHandling(dummyTx, peers.ref, Set(actor)))
    }

    def givenTransactionIsCorrectlyBroadcast(): Unit = {
      transactionBroadcastActor.expectAskWithReply {
        case FinishExchange => ExchangeFinished(TransactionPublished(dummyTx, dummyTx))
      }
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
        WatchPublicKey(counterpart.bitcoinKey),
        WatchPublicKey(user.bitcoinKey))
      blockchain.expectMsgAllOf(
        RetrieveTransaction(deposits.buyer),
        RetrieveTransaction(deposits.seller)
      )
    }
  }

  "The exchange actor" should "report an exchange success when handshake, exchange and broadcast work" in
    new Fixture {
      startExchange()
      givenHandshakeSuccess()
      givenMicropaymentChannelSuccess()
      givenTransactionIsCorrectlyBroadcast()
      listener.expectMsg(ExchangeSuccess(completedExchange))
      listener.expectMsgClass(classOf[Terminated])
      system.stop(actor)
    }

  it should "forward progress reports" in new Fixture {
    startExchange()
    val progressUpdate = ExchangeUpdate(runningExchange.increaseProgress(1.BTC, 0.EUR))
    givenHandshakeSuccess()
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, progressUpdate)
    listener.expectMsg(progressUpdate)
    system.stop(actor)
  }

  it should "report a failure if the handshake fails" in new Fixture {
    startExchange()
    val error = new Error("Handshake error")
    handshakeActor.expectCreation()
    handshakeActor.probe.send(actor, HandshakeFailure(error, refundTx = None))
    listener.expectMsgType[ExchangeFailure]
    listener.expectTerminated(actor)
    system.stop(actor)
  }

  it should "report a failure and ask the broadcaster publish the refund if commitments are invalid" in
    new Fixture {
      startExchange()
      givenHandshakeSuccessWithInvalidCounterpartCommitment()
      givenTransactionIsCorrectlyBroadcast()
      inside (listener.expectMsgType[ExchangeFailure].exchange.state.cause) {
        case Exchange.Abortion(Exchange.InvalidCommitments(Both(Failure(_), Success(_)))) =>
      }
      listener.expectTerminated(actor)
      system.stop(actor)
    }

  it should "report a failure if the actual exchange fails" in new Fixture {
    startExchange()
    givenHandshakeSuccess()

    val error = new Error("exchange failure")
    givenMicropaymentChannelCreation()
    micropaymentChannelActor.probe.send(actor, MicroPaymentChannelActor.ChannelFailure(1, error))

    givenTransactionIsCorrectlyBroadcast()

    listener.expectMsgType[ExchangeFailure]
    listener.expectMsgClass(classOf[Terminated])
    system.stop(actor)
  }

  it should "report a failure if the broadcast failed" in new Fixture {
    startExchange()
    givenHandshakeSuccess()
    givenMicropaymentChannelSuccess()
    val broadcastError = new Error("failed to broadcast")
    transactionBroadcastActor.expectAskWithReply {
      case FinishExchange => ExchangeFinishFailure(broadcastError)
    }
    listener.expectMsgType[ExchangeFailure].exchange.state.cause shouldBe Exchange.NoBroadcast
    listener.expectMsgClass(classOf[Terminated])
    system.stop(actor)
  }

  it should "report a failure if the broadcast succeeds with an unexpected transaction" in
    new Fixture {
      startExchange()
      givenHandshakeSuccess()
      givenMicropaymentChannelSuccess()
      val unexpectedTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      transactionBroadcastActor.expectAskWithReply {
        case FinishExchange => ExchangeFinished(TransactionPublished(unexpectedTx, unexpectedTx))
      }
      inside(listener.expectMsgType[ExchangeFailure].exchange.state) {
        case Exchange.Failed(Exchange.UnexpectedBroadcast, _, _, Some(`unexpectedTx`)) =>
      }
      listener.expectMsgClass(classOf[Terminated])
      system.stop(actor)
    }

  it should "report a failure if the broadcast is forcefully finished because it took too long" in
    new Fixture {
      startExchange()
      givenHandshakeSuccess()
      givenMicropaymentChannelCreation()
      val midWayTx = ImmutableTransaction {
        val newTx = dummyTx.get
        newTx.setLockTime(40)
        newTx
      }
      transactionBroadcastActor.expectNoMsg()
      transactionBroadcastActor.probe
        .send(actor, ExchangeFinished(TransactionPublished(midWayTx, midWayTx)))
      val failedState = listener.expectMsgType[ExchangeFailure].exchange.state
      failedState.cause shouldBe Exchange.PanicBlockReached
      failedState.transaction.value shouldBe midWayTx
      listener.expectMsgClass(classOf[Terminated])
      system.stop(actor)
    }
}
