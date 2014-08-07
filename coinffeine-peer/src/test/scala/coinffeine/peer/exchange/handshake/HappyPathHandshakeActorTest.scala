package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.bitcoin.Implicits._
import coinffeine.model.bitcoin.{Hash, ImmutableTransaction, TransactionSignature}
import coinffeine.model.exchange.{Both, ExchangeId}
import coinffeine.model.network.PeerId
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor._
import coinffeine.peer.exchange.handshake.HandshakeActor.HandshakeSuccess
import coinffeine.peer.exchange.protocol.MockExchangeProtocol
import coinffeine.protocol.gateway.MessageGateway.{ReceiveMessage, Subscribe}
import coinffeine.protocol.messages.arbitration.CommitmentNotification
import coinffeine.protocol.messages.handshake._

class HappyPathHandshakeActorTest extends HandshakeActorTest("happy-path") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitRefundSignatureTimeout = 1 minute,
    refundSignatureAbortTimeout = 1 minute
  )

  "Handshake happy path" should "subscribe to the relevant messages when initialized" in {
    gateway.expectNoMsg()
    givenActorIsInitialized()
    val Subscribe(filter) = gateway.expectMsgClass(classOf[Subscribe])
    val otherId = ExchangeId("other-id")
    val relevantPeerHandshake =
      PeerHandshake(exchange.id, handshake.exchange.counterpart.bitcoinKey.publicKey, "foo")
    val relevantSignatureRequest =
      RefundSignatureRequest(exchange.id, ImmutableTransaction(handshake.counterpartRefund))
    val irrelevantSignatureRequest =
      RefundSignatureRequest(otherId, ImmutableTransaction(handshake.counterpartRefund))
    filter(fromCounterpart(relevantPeerHandshake)) should be (true)
    filter(fromBroker(relevantPeerHandshake)) should be (false)
    filter(fromCounterpart(relevantSignatureRequest)) should be (true)
    filter(ReceiveMessage(relevantSignatureRequest, PeerId("other"))) should be (false)
    filter(fromCounterpart(irrelevantSignatureRequest)) should be (false)
    filter(fromCounterpart(
      RefundSignatureResponse(exchange.id, MockExchangeProtocol.RefundSignature))) should be (true)
    filter(fromBroker(CommitmentNotification(exchange.id, Both(mock[Hash], mock[Hash])))) should be (true)
    filter(fromBroker(ExchangeAborted(exchange.id, "failed"))) should be (true)
    filter(fromCounterpart(ExchangeAborted(exchange.id, "failed"))) should be (false)
    filter(fromBroker(ExchangeAborted(otherId, "failed"))) should be (false)
  }

  it should "send peer handshake" in {
    shouldForwardPeerHandshake()
  }

  it should "request refund transaction signature after getting counterpart peer handshake" in {
    givenCounterpartPeerHandshake()
    shouldCreateDeposits()
    shouldForwardRefundSignatureRequest()
    blockchain.expectMsg(WatchMultisigKeys(handshake.exchange.requiredSignatures.toSeq))
  }

  it should "reject signature of invalid counterpart refund transactions" in {
    val invalidRequest =
      RefundSignatureRequest(exchange.id, ImmutableTransaction(handshake.invalidRefundTransaction))
    gateway.send(actor, fromCounterpart(invalidRequest))
    gateway.expectNoMsg(100 millis)
  }

  it should "sign counterpart refund while waiting for our refund" in {
    shouldSignCounterpartRefund()
  }

  it should "don't be fooled by invalid refund TX or source and resubmit signature request" in {
    gateway.send(
      actor, fromCounterpart(RefundSignatureResponse(exchange.id, mock[TransactionSignature])))
    shouldForwardRefundSignatureRequest()
  }

  it should "send commitment TX to the broker after getting his refund TX signed" in {
    givenValidRefundSignatureResponse()
    shouldForward (ExchangeCommitment(exchange.id, handshake.myDeposit)) to brokerId
  }

  it should "sign counterpart refund after having our refund signed" in {
    shouldSignCounterpartRefund()
  }

  it should "wait until the broker publishes commitments" in {
    listener.expectNoMsg(100 millis)
    gateway.send(actor, fromBroker(CommitmentNotification(
      exchange.id,
      Both(
        handshake.myDeposit.get.getHash,
        handshake.counterpartCommitmentTransaction.getHash
      )
    )))
    val confirmations = protocolConstants.commitmentConfirmations
    blockchain.expectMsgAllOf(
      WatchTransactionConfirmation(handshake.myDeposit.get.getHash, confirmations),
      WatchTransactionConfirmation(handshake.counterpartCommitmentTransaction.getHash, confirmations)
    )
  }

  it should "wait until commitments are confirmed" in {
    listener.expectNoMsg(100 millis)
    val expectedCommitments = Both(
      buyer = handshake.myDeposit,
      seller = ImmutableTransaction(handshake.counterpartCommitmentTransaction)
    )
    for (tx <- expectedCommitments.toSeq) {
      blockchain.send(actor, TransactionConfirmed(tx.get.getHash, 1))
    }
    expectedCommitments.foreach { tx =>
      blockchain.expectMsg(RetrieveTransaction(tx.get.getHash))
      blockchain.reply(TransactionFound(tx.get.getHash, tx))
    }
    listener.expectMsgPF() {
      case HandshakeSuccess(_, `expectedCommitments`, handshake.`mySignedRefund`) =>
    }
  }

  it should "finally terminate himself" in {
    listener.expectTerminated(actor)
  }
}
