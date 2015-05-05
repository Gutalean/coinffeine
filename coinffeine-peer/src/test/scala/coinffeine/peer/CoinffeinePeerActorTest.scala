package coinffeine.peer

import scala.concurrent.duration._

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import org.bitcoinj.params.TestNet3Params

import coinffeine.common.akka.Service
import coinffeine.common.akka.test.{AkkaSpec, MockSupervisedActor}
import coinffeine.model.bitcoin.{Address, ImmutableTransaction, MutableTransaction}
import coinffeine.model.currency._
import coinffeine.model.market._
import coinffeine.peer.CoinffeinePeerActor._
import coinffeine.peer.bitcoin.BitcoinPeerActor
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.market.MarketInfoActor.{RequestOpenOrders, RequestQuote}
import coinffeine.peer.payment.PaymentProcessorActor.RetrieveBalance
import coinffeine.protocol.messages.brokerage.{OpenOrdersRequest, QuoteRequest}

class CoinffeinePeerActorTest extends AkkaSpec(ActorSystem("PeerActorTest")) {

  "A peer" must "delegate quote requests" in new StartedFixture {
    peer ! QuoteRequest(Market(Euro))
    marketInfo.expectForward(RequestQuote(Market(Euro)), self)
    peer ! OpenOrdersRequest(Market(UsDollar))
    marketInfo.expectForward(RequestOpenOrders(Market(UsDollar)), self)
  }

  it must "delegate order placement" in new StartedFixture {
    shouldForwardMessage(OpenOrder(Order.randomLimit(Bid, 10.BTC, Price(300.EUR))), orders)
  }

  it must "delegate order cancellation" in new StartedFixture {
    shouldForwardMessage(CancelOrder(OrderId.random()), orders)
  }

  it must "delegate fiat balance requests" in new StartedFixture {
    shouldForwardMessage(RetrieveBalance(UsDollar), paymentProcessor)
  }

  it must "stop delegates on stop" in new StartedFixture {
    peer ! Service.Stop
    paymentProcessor.expectMsg(Service.Stop)
    bitcoinPeer.expectMsg(Service.Stop)
    gateway.expectMsg(Service.Stop)
  }

  it must "process wallet funds withdraw" in new StartedFixture with WithdrawParams {
    requester.send(peer, withdrawRequest)
    wallet.expectMsg(createTxRequest)
    wallet.reply(createTxSuccess)
    bitcoinPeer.expectAskWithReply { case `publishTxRequest` => publishTxSuccess }
    requester.expectMsg(withdrawSuccess)
  }

  it must "fail process wallet funds on tx creation errors" in new StartedFixture with WithdrawParams {
    requester.send(peer, withdrawRequest)
    wallet.expectMsg(createTxRequest)
    wallet.reply(createTxFailure)
    bitcoinPeer.expectNoMsg()
    val WalletFundsWithdrawFailure(`amount`, `someAddress`, _) =
      requester.expectMsgType[WalletFundsWithdrawFailure]
  }

  it must "fail process wallet funds on tx publishing errors" in new StartedFixture with WithdrawParams {
    requester.send(peer, withdrawRequest)
    wallet.expectMsg(createTxRequest)
    wallet.reply(createTxSuccess)
    bitcoinPeer.expectAskWithReply { case `publishTxRequest` => publishTxFailure }
    val WalletFundsWithdrawFailure(`amount`, `someAddress`, _) =
      requester.expectMsgType[WalletFundsWithdrawFailure]
  }

  trait Fixture {
    val requester, wallet, blockchain = TestProbe()
    val alarmReporter, gateway, marketInfo, orders, bitcoinPeer, paymentProcessor =
      new MockSupervisedActor()
    private val propsCatalogue = PropsCatalogue(
      alarmReporter = alarmReporter.props(),
      gateway = system => gateway.props(system),
      marketInfo = market => marketInfo.props(market),
      orderSupervisor = collaborators => orders.props(collaborators),
      paymentProcessor = paymentProcessor.props(),
      bitcoinPeer = bitcoinPeer.props())
    val peer = system.actorOf(Props(new CoinffeinePeerActor(propsCatalogue, 1.second.dilated)))

    def shouldForwardMessage(message: Any, delegate: MockSupervisedActor): Unit = {
      peer ! message
      delegate.expectForward(message, self)
    }

    def shouldForwardMessage(message: Any, delegate: TestProbe): Unit = {
      peer ! message
      delegate.expectMsg(message)
      delegate.sender() should be (self)
    }

    def shouldCreateActors(actors: MockSupervisedActor*): Unit = {
      actors.foreach(_.expectCreation())
    }

    def shouldRequestStart[Args](actor: MockSupervisedActor, args: Args): Unit = {
      actor.expectAskWithReply {
        case Service.Start(`args`) => Service.Started
      }
    }
  }

  trait StartedFixture extends Fixture {
    // Firstly, the actors are created before peer is started
    shouldCreateActors(alarmReporter, gateway, paymentProcessor, bitcoinPeer, marketInfo)

    // Then we start the actor
    requester.send(peer, Service.Start {})

    // Then it must request the alarm reporter to start
    shouldRequestStart(alarmReporter, {})

    // Then it must request the payment processor to start
    shouldRequestStart(paymentProcessor, {})

    // Then it must request the Bitcoin network to start
    shouldRequestStart(bitcoinPeer, {})

    // Then request to join to the Coinffeine network
    shouldRequestStart(gateway, {})

    // Then request the wallet and blockchain actors from bitcoin actor
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveWalletActor => BitcoinPeerActor.WalletActorRef(wallet.ref)
    }
    bitcoinPeer.expectAskWithReply {
      case BitcoinPeerActor.RetrieveBlockchainActor => BitcoinPeerActor.BlockchainActorRef(blockchain.ref)
    }

    // Then request the order supervisor to initialize
    orders.expectCreation()

    // And finally indicate it succeed to start
    requester.expectMsg(Service.Started)
  }

  trait WithdrawParams {
    val network = new TestNet3Params
    val amount = 1.BTC
    val someError = new Error
    val someAddress = new Address(null, "mrmrQ9wrxjfdUGaa9KaUsTfQFqTVKr57B8")
    val tx = ImmutableTransaction(new MutableTransaction(network))
    val createTxRequest = WalletActor.CreateTransaction(amount, someAddress)
    val publishTxRequest = BitcoinPeerActor.PublishTransaction(tx)
    val withdrawRequest = WithdrawWalletFunds(amount, someAddress)
    val createTxSuccess = WalletActor.TransactionCreated(createTxRequest, tx)
    val createTxFailure = WalletActor.TransactionCreationFailure(createTxRequest, someError)
    val publishTxSuccess = BitcoinPeerActor.TransactionPublished(tx, tx)
    val publishTxFailure = BitcoinPeerActor.TransactionNotPublished(tx, someError)
    val withdrawSuccess = WalletFundsWithdrawn(amount, someAddress, tx)
  }
}
