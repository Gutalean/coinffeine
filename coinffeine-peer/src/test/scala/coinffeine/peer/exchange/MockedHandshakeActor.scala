package coinffeine.peer.exchange

import akka.actor.{Actor, ActorSystem, PoisonPill, Props}
import akka.testkit.TestProbe
import org.joda.time.DateTime

import coinffeine.model.Both
import coinffeine.model.bitcoin.ImmutableTransaction
import coinffeine.model.exchange.HandshakeFailureCause.SignatureTimeout
import coinffeine.peer.exchange.handshake.HandshakeActor
import coinffeine.peer.exchange.handshake.HandshakeActor._
import coinffeine.peer.exchange.protocol.FakeExchangeProtocol
import coinffeine.peer.exchange.test.CoinffeineClientTest.Perspective

class MockedHandshakeActor(perspective: Perspective)(implicit system: ActorSystem) {

  private var handshakeResult: HandshakeResult = _
  private val finishProbe = TestProbe()

  private class HandshakeStub extends Actor {
    override def preStart(): Unit = {
      context.parent ! MockedHandshakeActor.this.synchronized(handshakeResult)
    }
    override val receive: Receive = {
      case msg @ HandshakeActor.Finish =>
        finishProbe.ref forward msg
        self ! PoisonPill
    }
  }

  val props: Props = Props(new HandshakeStub)

  def givenHandshakeWillSucceed(commitments: Both[ImmutableTransaction],
                                refundTx: ImmutableTransaction) = synchronized {
    import perspective._
    handshakeResult = HandshakeSuccess(
      exchange = exchange.handshake(user, counterpart, ExchangeTimestamps.handshakingStart),
      bothCommitments = commitments,
      refundTx = refundTx,
      timestamp = ExchangeTimestamps.completion
    )
  }

  def givenFailingHandshake(completedOn: DateTime): Unit = synchronized {
    handshakeResult = HandshakeFailure(SignatureTimeout, completedOn)
  }

  def givenHandshakeWillSucceedWithInvalidCounterpartCommitment(
     refundTx: ImmutableTransaction): Unit = {
    val commitments = Both(buyer = FakeExchangeProtocol.InvalidDeposit, seller = refundTx)
    givenHandshakeWillSucceed(commitments, refundTx)
  }

  def expectFinished(): Unit = {
    finishProbe.expectMsg(HandshakeActor.Finish)
  }
}
