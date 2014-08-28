package coinffeine.peer.exchange.handshake

import scala.concurrent.duration._

import coinffeine.peer.ProtocolConstants
import coinffeine.peer.bitcoin.BlockchainActor
import coinffeine.peer.bitcoin.BlockchainActor.TransactionRejected
import coinffeine.peer.exchange.handshake.HandshakeActor._

class RejectedTxHandshakeActorTest extends HandshakeActorTest("rejected-tx") {

  override def protocolConstants = ProtocolConstants(
    commitmentConfirmations = 1,
    resubmitHandshakeMessagesTimeout = 1.minute,
    refundSignatureAbortTimeout = 1.minute
  )

  "Handshakes in which TX are rejected" should "have a failed handshake result" in {
    shouldForwardPeerHandshake()
    givenCounterpartPeerHandshake()
    shouldCreateDeposits()
    shouldForwardRefundSignatureRequest()
    expectNoMsg()
    givenValidRefundSignatureResponse()
    shouldForwardCommitmentToBroker()
    givenCommitmentPublicationNotification()

    blockchain.fishForMessage() {
      case _: BlockchainActor.WatchTransactionConfirmation => true
      case _ => false
    }
    blockchain.reply(TransactionRejected(handshake.counterpartCommitmentTransaction.getHash))

    val result = listener.expectMsgClass(classOf[HandshakeFailure])
    result.e.toString should include (
      s"transaction ${handshake.counterpartCommitmentTransaction.getHash} (counterpart) was rejected")
  }

  it should "terminate" in {
    listener.expectTerminated(actor)
  }
}
