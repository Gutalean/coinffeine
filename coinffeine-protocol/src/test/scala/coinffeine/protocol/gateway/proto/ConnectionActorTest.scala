package coinffeine.protocol.gateway.proto

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace
import scala.util.{Success, Try}

import akka.actor.Props
import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork
import coinffeine.protocol.gateway.p2p.P2PNetwork.Connection
import coinffeine.protocol.gateway.proto.ConnectionActor.Message

class ConnectionActorTest extends AkkaSpec {

  val idleTime = 100.millis.dilated
  val message1 = Message(Array[Byte](1))
  val message2 = Message(Array[Byte](2))

  "A protobuf connection actor" should "open a connection and send all messages received meanwhile" in
    new Fixture {
      val connection = new MockConnection
      actor ! message1
      actor ! message2
      expectNoMsg(idleTime)
      session.completeConnection(Success(connection))
      connection.probe.expectMsg(message1.bytes)
      connection.probe.expectMsg(message2.bytes)
    }

  it should "close connection on stop" in new Fixture {
    val connection = new MockConnection
    session.completeConnection(Success(connection))
    actor ! message1
    connection.probe.expectMsg(message1.bytes)
    system.stop(actor)
    connection.probe.expectMsg("closed")
  }

  it should "reset the connection after send failure" in new Fixture {
    val badConnection = new MockFaultyConnection
    session.completeConnection(Success(badConnection))
    actor ! message1
    badConnection.probe.expectMsg("closed")

    val goodConnection = new MockConnection
    session.completeConnection(Success(goodConnection))
    actor ! message2
    goodConnection.probe.expectMsg(message2.bytes)
  }

  class MockSession extends P2PNetwork.Session {
    override val brokerId = PeerId.hashOf("broker")
    val receiverId = PeerId.hashOf("receiver")

    private var connectPromise = Promise[Connection]()

    override def connect(peerId: PeerId): Future[Connection] =
      if (peerId == receiverId) connectPromise.future
      else Future.failed(new Error("wrong id") with NoStackTrace)

    override def close(): Future[Unit] = ???

    def completeConnection(result: Try[Connection]): Unit = {
      connectPromise.complete(result)
      connectPromise = Promise[Connection]()
    }
  }

  class MockConnection extends P2PNetwork.Connection {
    val probe = TestProbe()

    override def send(payload: Array[Byte]) = Future.successful {
      probe.ref ! payload
    }

    override def close() = Future.successful {
      probe.ref ! "closed"
    }
  }

  class MockFaultyConnection extends MockConnection {
    override def send(payload: Array[Byte]) = {
      Future.failed(new Exception("injected error") with NoStackTrace)
    }
  }

  trait Fixture {
    val session = new MockSession
    val actor = system.actorOf(Props(new ConnectionActor(session, session.receiverId)))
  }
}
