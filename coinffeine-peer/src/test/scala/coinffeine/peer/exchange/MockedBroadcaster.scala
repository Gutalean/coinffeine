package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import akka.actor._
import akka.testkit._

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.peer.exchange.broadcast.TransactionBroadcaster

class MockedBroadcaster(implicit system: ActorSystem) {

  private val probe = TestProbe()

  private class ActorStub extends Actor {
    override val receive: Receive = {
      case TransactionBroadcaster.Finish =>
        probe.ref ! TransactionBroadcaster.Finish
        self ! PoisonPill
    }
  }

  val props: Props = Props(new ActorStub)

  def expectFinished(): Unit = {
    probe.expectMsg(TransactionBroadcaster.Finish)
  }
}

object MockedBroadcaster {
  val DummyTx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
  val Exception = new Exception("injected broadcast failure") with NoStackTrace
  val PanicDelay = 100.millis
}
