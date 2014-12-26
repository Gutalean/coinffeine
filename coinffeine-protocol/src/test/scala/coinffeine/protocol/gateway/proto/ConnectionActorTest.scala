package coinffeine.protocol.gateway.proto

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NoStackTrace

import akka.actor.Props
import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.p2p.P2PNetwork
import coinffeine.protocol.gateway.p2p.P2PNetwork.Connection
import coinffeine.protocol.gateway.proto.ConnectionActor.{Message, Ping, PingBack}

class ConnectionActorTest extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("connection")) {

  val idleTime = 100.millis.dilated
  val receiverId = PeerId.hashOf("receiver")
  val message1 = Message(Array[Byte](1))
  val message2 = Message(Array[Byte](2))

  "A protobuf connection actor" should "open a connection and send all messages received meanwhile" in
    new Fixture {
      actor ! message1
      actor ! message2
      expectNoMsg(idleTime)
      val connection = new MockConnection
      session.connectionSuccess(0, connection)
      connection.expectSendRequest(message1)
      connection.expectSendRequest(message2)
    }

  it should "close connection on stop" in new Fixture {
    val connection = new MockConnection
    session.connectionSuccess(0, connection)
    actor ! message1
    connection.expectSendRequest(message1)
    system.stop(actor)
    connection.expectBeingClosed()
  }

  it should "reset the connection after send failure" in new Fixture {
    val badConnection = new MockFaultyConnection
    session.connectionSuccess(0, badConnection)
    actor ! message1
    badConnection.expectBeingClosed()

    val goodConnection = new MockConnection
    session.connectionSuccess(1, goodConnection)
    actor ! message2
    goodConnection.expectSendRequest(message2)
  }

  it should "rethrow connection exceptions" in new Fixture {
    EventFilter[ConnectionException.type](occurrences = 1) intercept {
      session.connectionFailure(0)
    }
  }

  it should "ping when requested" in new Fixture {
    val connection = new MockConnection
    session.connectionSuccess(0, connection)
    actor ! Ping
    connection.expectPing()
  }

  it should "ping back when requested" in new Fixture {
    val connection = new MockConnection
    session.connectionSuccess(0, connection)
    actor ! PingBack
    connection.expectPingBack()
  }

  case object ConnectionException extends Exception("Injected connection exception") with NoStackTrace

  class MockSession extends P2PNetwork.Session {
    private val connectionPromises = Stream.continually(Promise[Connection]())
    private var nextConnection = 0

    override val brokerId = PeerId.hashOf("broker")

    override def connect(peerId: PeerId): Future[Connection] =
      if (peerId != receiverId) Future.failed(ConnectionException)
      else synchronized {
        val result = connectionPromises(nextConnection).future
        nextConnection += 1
        result
      }

    override def close(): Future[Unit] = Future.successful {}

    def connectionSuccess(index: Int, connection: Connection): Unit = {
      connectionPromises(index).success(connection)
    }

    def connectionFailure(index: Int): Unit = {
      connectionPromises(index).failure(ConnectionException)
    }
  }

  class MockConnection extends P2PNetwork.Connection {
    private val probe = TestProbe()

    override def send(payload: Array[Byte]) = Future.successful { probe.ref ! payload }
    override def ping() = Future.successful { probe.ref ! "ping!" }
    override def pingBack() = Future.successful { probe.ref ! "pong!" }
    override def close() = Future.successful { probe.ref ! "closed" }

    def expectPing(): Unit = { probe.expectMsg("ping!") }
    def expectPingBack(): Unit = { probe.expectMsg("pong!") }
    def expectSendRequest(message: Message): Unit = { probe.expectMsg(message.bytes) }
    def expectBeingClosed(): Unit = { probe.expectMsg("closed") }
  }

  class MockFaultyConnection extends MockConnection {
    override def send(payload: Array[Byte]) = {
      Future.failed(new Exception("injected error") with NoStackTrace)
    }
  }

  trait Fixture {
    val session = new MockSession
    val actor = system.actorOf(Props(new ConnectionActor(session, receiverId)))
  }
}
