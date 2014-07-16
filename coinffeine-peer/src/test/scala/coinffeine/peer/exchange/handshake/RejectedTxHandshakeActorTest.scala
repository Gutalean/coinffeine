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
    resubmitRefundSignatureTimeout = 10 seconds,
    refundSignatureAbortTimeout = 100 millis
  )

  "Handshakes in which TX are rejected" should "have a failed handshake result" in {
    givenActorIsInitialized()
    givenCounterpartPeerHandshake()
    givenValidRefundSignatureResponse()
    gateway.send(actor, fromBroker(CommitmentNotification(
      exchange.id,
      Both(handshake.myDeposit.get.getHash, handshake.counterpartCommitmentTransaction.getHash)
    )))
    blockchain.send(actor, TransactionRejected(handshake.counterpartCommitmentTransaction.getHash))

    val result = listener.expectMsgClass(classOf[HandshakeFailure])
    result.e.toString should include (
      s"transaction ${handshake.counterpartCommitmentTransaction.getHash} (counterpart) was rejected")
  }

  it should "terminate" in {
    listener.expectTerminated(actor)
  }
}
