package coinffeine.peer.exchange.handshake

import akka.actor.Props
import akka.testkit.TestProbe
import org.scalatest.mock.MockitoSugar

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.test.BitcoinjTest
import coinffeine.model.bitcoin.{Address, ImmutableTransaction}
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.exchange.handshake.HandshakeActor.StartHandshake
import coinffeine.peer.exchange.protocol.{MockExchangeProtocol, MockHandshake}
import coinffeine.peer.exchange.test.CoinffeineClientTest
import coinffeine.peer.exchange.test.CoinffeineClientTest.SellerPerspective
import coinffeine.protocol.messages.handshake.{PeerHandshake, RefundSignatureRequest, RefundSignatureResponse}

/** Test fixture for testing the handshake actor interaction, one derived class per scenario. */
abstract class HandshakeActorTest(systemName: String)
  extends CoinffeineClientTest(systemName) with SellerPerspective with BitcoinjTest with MockitoSugar {

  def protocolConstants: ProtocolConstants

  lazy val handshake = new MockHandshake(handshakingExchange)
  val listener = TestProbe()
  val blockchain = TestProbe()
  val actor = system.actorOf(Props(new HandshakeActor(new MockExchangeProtocol)), "handshake-actor")
  listener.watch(actor)

  def givenActorIsInitialized(): Unit = {
    val changeAddress = new Address(null, "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL")
    val unspentOutputs = Seq.empty
    actor ! StartHandshake(exchange, userRole, user, unspentOutputs, changeAddress,
      protocolConstants, gateway.ref, blockchain.ref, Set(listener.ref))
  }

  def givenCounterpartPeerHandshake(): Unit = {
    val peerHandshake =
      PeerHandshake(exchange.id, counterpart.bitcoinKey.publicKey, counterpart.paymentProcessorAccount)
    gateway.send(actor, fromCounterpart(peerHandshake))
  }

  def givenValidRefundSignatureResponse() = {
    val validSignature = RefundSignatureResponse(exchange.id, MockExchangeProtocol.RefundSignature)
    gateway.send(actor, fromCounterpart(validSignature))
  }

  def shouldForwardPeerHandshake(): Unit = {
    val peerHandshake =
      PeerHandshake(exchange.id, user.bitcoinKey.publicKey, user.paymentProcessorAccount)
    shouldForward (peerHandshake) to counterpartConnection
  }

  def shouldForwardRefundSignatureRequest(): Unit = {
    val refundSignatureRequest = RefundSignatureRequest(exchange.id, handshake.myUnsignedRefund)
    shouldForward (refundSignatureRequest) to counterpartConnection
  }

  def shouldSignCounterpartRefund(): Unit = {
    val request = RefundSignatureRequest(exchange.id, ImmutableTransaction(handshake.counterpartRefund))
    gateway.send(actor, fromCounterpart(request))
    val refundSignatureRequest =
      RefundSignatureResponse(exchange.id, MockExchangeProtocol.CounterpartRefundSignature)
    shouldForward (refundSignatureRequest) to counterpartConnection
  }

  override protected def resetBlockchainBetweenTests = false
}
