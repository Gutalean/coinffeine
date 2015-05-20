package coinffeine.peer.exchange

import scala.concurrent.duration._
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

import akka.actor._
import akka.testkit._

import coinffeine.model.bitcoin.test.CoinffeineUnitTestNetwork
import coinffeine.model.bitcoin.{ImmutableTransaction, MutableTransaction}
import coinffeine.peer.bitcoin.BitcoinPeerActor.TransactionPublished
import coinffeine.peer.exchange.broadcast.TransactionBroadcaster

class MockedBroadcaster(implicit system: ActorSystem) {

  private var timeout: Duration = Duration.Undefined
  private var result: Try[ImmutableTransaction] = Success(MockedBroadcaster.DummyTx)
  private val probe = TestProbe()

  private class ActorStub extends Actor {

    override def preStart(): Unit = MockedBroadcaster.this.synchronized {
      context.setReceiveTimeout(timeout)
    }

    override val receive: Receive = {
      case TransactionBroadcaster.PublishBestTransaction | ReceiveTimeout =>
        val response = MockedBroadcaster.this.synchronized {
          result match {
            case Success(tx) =>
              TransactionBroadcaster.SuccessfulBroadcast(TransactionPublished(tx, tx))
            case Failure(cause) =>
              TransactionBroadcaster.FailedBroadcast(cause)
          }
        }
        sender() ! response

      case TransactionBroadcaster.Finish =>
        probe.ref ! TransactionBroadcaster.Finish
        self ! PoisonPill
    }
  }

  val props: Props = Props(new ActorStub)

  def givenBroadcasterWillSucceed(
      tx: ImmutableTransaction = MockedBroadcaster.DummyTx): Unit = synchronized {
    result = Success(tx)
    timeout = Duration.Undefined
  }

  def givenBroadcasterWillPanic(tx: ImmutableTransaction): Unit = synchronized {
    result = Success(tx)
    timeout = MockedBroadcaster.PanicDelay.dilated
  }

  def givenBroadcasterWillFail(): Unit = synchronized {
    result = Failure(MockedBroadcaster.Exception)
    timeout = Duration.Undefined
  }

  def expectFinished(): Unit = {
    probe.expectMsg(TransactionBroadcaster.Finish)
  }
}

object MockedBroadcaster {
  val DummyTx = ImmutableTransaction(new MutableTransaction(CoinffeineUnitTestNetwork))
  val Exception = new Exception("injected broadcast failure") with NoStackTrace
  val PanicDelay = 100.millis
}
