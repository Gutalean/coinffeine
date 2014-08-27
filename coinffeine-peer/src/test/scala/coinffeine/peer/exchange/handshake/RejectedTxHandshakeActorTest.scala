package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.model.exchange.Both
import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor.TransactionRejected
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.protocol.messages.arbitration.CommitmentNotification

class RejectedTxHandshakeActorTest extends HandshakeActorTest("rejected-tx") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitRefundSignatureTimeout = 1.minute,
    refundSignatureAbortTimeout = 1.minute
  )

  "Handshakes in which TX are rejected" should "have a failed handshake result" in {
    givenActorIsInitialized()
    givenActorIsSubscribedToMessages()
    shouldForwardPeerHandshake()
    givenCounterpartPeerHandshake()
    shouldCreateDeposits()
    shouldForwardRefundSignatureRequest()
    expectNoMsg()
    givenValidRefundSignatureResponse()
    val notification = CommitmentNotification(
      exchange.id,
      Both(handshake.myDeposit.get.getHash, handshake.counterpartCommitmentTransaction.getHash)
    )
    gateway.relayMessageFromBroker(notification)
    blockchain.send(actor, TransactionRejected(handshake.counterpartCommitmentTransaction.getHash))

    val result = listener.expectMsgClass(classOf[HandshakeFailure])
    result.e.toString should include (
      s"transaction ${handshake.counterpartCommitmentTransaction.getHash} (counterpart) was rejected")
  }

  it should "terminate" in {
    listener.expectTerminated(actor)
  }
}
