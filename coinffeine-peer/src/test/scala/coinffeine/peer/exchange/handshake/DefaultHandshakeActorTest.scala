package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit.TestProbe
import org.joda.time.DateTime

import coinffeine.model.Both
import coinffeine.model.bitcoin._
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.wallet.WalletActor
import coinffeine.peer.exchange.ExchangeActor.ExchangeUpdate
import coinffeine.peer.exchange.protocol.{MockExchangeProtocol, MockHandshake}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.protocol.messages.arbitration.{CommitmentNotification, CommitmentNotificationAck}
import coinffeine.protocol.messages.handshake._

/** Test fixture for testing the handshake actor interaction, one derived class per scenario. */
abstract class DefaultHandshakeActorTest(systemName: String)
  extends CoinffeineClientTest(systemName) with SellerPerspective {

  def protocolConstants: ProtocolConstants

  lazy val handshake = new MockHandshake(handshakingExchange)
  val idleTime = 100.millis
  val listener, blockchain, wallet = TestProbe()
  var actor: ActorRef = _
  startActor()

  private val peerHandshake =
    PeerHandshake(exchange.id, user.bitcoinKey.publicKey, user.paymentProcessorAccount)
  private val refundSignatureRequest =
    RefundSignatureRequest(exchange.id, handshake.myUnsignedRefund)

  def startActor(): Unit = {
    actor = system.actorOf(DefaultHandshakeActor.props(
      DefaultHandshakeActor.ExchangeToStart(exchange, DateTime.now(), user),
      DefaultHandshakeActor.Collaborators(gateway.ref, blockchain.ref, wallet.ref, listener.ref),
      DefaultHandshakeActor.ProtocolDetails(new MockExchangeProtocol, protocolConstants)
    ))
    listener.watch(actor)
  }

  def restartActor(): Unit = {
    system.stop(actor)
    listener.expectTerminated(actor)
    startActor()
  }

  def givenCounterpartPeerHandshake(): Unit = {
    val peerHandshake =
      PeerHandshake(exchange.id, counterpart.bitcoinKey.publicKey, counterpart.paymentProcessorAccount)
    gateway.relayMessage(peerHandshake, counterpartId)
    listener.expectMsgType[ExchangeUpdate]
  }

  def givenValidRefundSignatureResponse() = {
    val validSignature = RefundSignatureResponse(exchange.id, MockExchangeProtocol.RefundSignature)
    gateway.relayMessage(validSignature, counterpartId)
  }

  def givenCommitmentPublicationNotification(): Unit = {
    val notification = CommitmentNotification(
      exchange.id,
      Both(
        handshake.myDeposit.get.getHash,
        handshake.counterpartCommitmentTransaction.getHash
      )
    )
    gateway.relayMessageFromBroker(notification)
  }

  def givenCounterpartSignatureRequest(): Unit = {
    val request =
      RefundSignatureRequest(exchange.id, ImmutableTransaction(handshake.counterpartRefund))
    gateway.relayMessage(request, counterpartId)
  }

  def shouldCreateDeposits(): Unit = {
    val request = wallet.expectMsgType[WalletActor.CreateDeposit]
    val depositAmounts = exchange.amounts.deposits.seller
    request.amount shouldBe depositAmounts.output
    request.transactionFee shouldBe depositAmounts.fee
    wallet.reply(WalletActor.DepositCreated(request, MockExchangeProtocol.DummyDeposit))
  }

  def shouldForwardPeerHandshake(): Unit = {
    gateway.expectForwarding(peerHandshake, counterpartId)
  }

  def shouldForwardRefundSignatureRequest(): Unit = {
    gateway.expectForwarding(refundSignatureRequest, counterpartId)
  }

  def shouldForwardPeerHandshakeAndRefundSignatureRequest(): Unit = {
    gateway.expectForwardingToAllOf(counterpartId, peerHandshake, refundSignatureRequest)
  }

  def shouldSignCounterpartRefund(): Unit = {
    val id = exchange.id
    gateway.fishForForwardingTo(counterpartId, hint = "counterpart refund signature") {
      case RefundSignatureRequest(_, _) => false
      case RefundSignatureResponse(`id`, MockExchangeProtocol.CounterpartRefundSignature) => true
    }
  }

  def shouldForwardCommitmentToBroker(): Unit = {
    gateway.expectForwardingToBroker(
      ExchangeCommitment(exchange.id, user.bitcoinKey.publicKey, handshake.myDeposit))
  }

  def shouldAckCommitmentNotification(): Unit = {
    gateway.expectForwardingToBroker(CommitmentNotificationAck(exchange.id))
  }
}
