package coinffeine.overlay.relay.server

import java.net.InetSocketAddress
import scala.concurrent.duration._
import scala.util.Random

import akka.io.Tcp
import akka.testkit._
import org.scalatest.OptionValues

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.overlay.OverlayId

class ServerActorTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("relay-server")) with OptionValues {

  val config = ServerConfig(
    bindAddress = "0.0.0.0",
    bindPort = 1234,
    identificationTimeout = 100.millis.dilated,
    maxFrameBytes = 1024
  )
  val localAddress = new InetSocketAddress("0.0.0.0", 1234)
  val idleTime = 200.millis.dilated

  "A server actor" should "bind to a port at start" in new FreshServer {
    expectSuccessfulBindOnStart()
  }

  it should "stop listening when asked to stop" in new BoundServer {
    server ! ServiceActor.Stop
    socketProbe.expectMsg(Tcp.Unbind)
    socketProbe.reply(Tcp.Unbound)
    expectMsg(ServiceActor.Stopped)
  }

  it should "fail to start when cannot bind" in new FreshServer {
    server ! ServiceActor.Start(config)
    val bindCommand = tcpProbe.expectMsgType[Tcp.Bind]
    tcpProbe.reply(Tcp.CommandFailed(bindCommand))
    expectMsgType[ServiceActor.StartFailure].cause.getMessage should
      include("Cannot bind to address")
  }

  it should "accept incoming client connections" in new BoundServer {
    val client = expectClientConnection()
    client.lastStatus shouldBe 'empty
  }

  it should "send an updated status message upon identification" in new BoundServer {
    val client1, client2 = expectClientConnection()
    client1.identifyAs(OverlayId(1))
    client1.lastStatus.value.networkSize shouldBe 1
    client2.identifyAs(OverlayId(2))
    client2.lastStatus.value.networkSize shouldBe 2
  }

  it should "relay messages between clients" in new BoundServer {
    val client1, client2 = expectClientConnection()
    client1.identifyAs(OverlayId(1))
    client2.identifyAs(OverlayId(2))

    client1.sendMessage(client2, "hello")
    client2.expectMessage(client1, "hello")
  }

  it should "log messages whose destination is not available on the network" in new BoundServer {
    val client = expectClientConnection()
    client.identifyAs(OverlayId(1))
    EventFilter.warning(start = "Cannot deliver message", occurrences = 1) intercept {
      client.sendMessage(OverlayId(2), "hello")
    }
  }

  it should "disconnect clients sending malformed frames" in new BoundServer {
    val client1, client2 = expectClientConnection()

    client1.sendMalformedFrame()
    client1.expectDisconnection()

    client2.identifyAs(OverlayId(2))
    client2.sendMalformedFrame()
    client2.expectDisconnection()
  }

  it should "disconnect clients sending too big frames" in new BoundServer {
    val client1, client2 = expectClientConnection()

    client1.sendFrameOfSize(config.maxFrameBytes + 1)
    client1.expectDisconnection()

    client2.identifyAs(OverlayId(2))
    client2.sendFrameOfSize(config.maxFrameBytes + 1)
    client2.expectDisconnection()
  }

  it should "disconnect clients sending invalid protobuf messages" in new BoundServer {
    val client1, client2 = expectClientConnection()

    client1.sendMalformedProtobuf()
    client1.expectDisconnection()

    client2.identifyAs(OverlayId(2))
    client2.sendMalformedProtobuf()
    client2.expectDisconnection()
  }

  it should "disconnect clients whose initial message is other than join" in new BoundServer {
    val client = expectClientConnection()
    client.sendMessage(OverlayId(1), "hi!")
    client.expectDisconnection()
  }

  it should "disconnect clients sending other than relay after joining" in new BoundServer {
    val client = expectClientConnection()
    client.identifyAs(OverlayId(1))
    client.sendStatusMessage()
    client.expectDisconnection()
  }

  it should "disconnect clients not yet identified after a timeout" in new BoundServer {
    val client = expectClientConnection()
    client.expectDisconnection()
  }

  it should "reject client identification if the overlay id is in use" in new BoundServer {
    val client1, client2 = expectClientConnection()
    client1.identifyAs(OverlayId(1))
    client2.unsuccessfullyIdentifyAs(OverlayId(1))
  }

  it should "send network status messages when the size of the network changes" in new BoundServer {
    val client1, client2 = expectClientConnection()
    client1.identifyAs(OverlayId(1))
    client2.identifyAs(OverlayId(2))
    expectNoMsg(idleTime)

    client1.disconnect()
    client2.expectStatusUpdate(networkSize = 1)
  }

  it should "send network status messages no faster than a configured rate" in new BoundServer {
    val statusListener = expectClientConnection()
    statusListener.identifyAs(OverlayId(1))

    for(index <- 2 to 10) {
      expectClientConnection().identifyAs(OverlayId(index))
    }

    statusListener.expectStatusUpdate(10)
    statusListener.expectNoMsg(idleTime)

    expectClientConnection().identifyAs(OverlayId(11))
    statusListener.expectStatusUpdate(11)
  }

  trait FreshServer {
    val tcpProbe, socketProbe = TestProbe()
    val server = system.actorOf(ServerActor.props(tcpProbe.ref))

    def expectSuccessfulBindOnStart(): Unit = {
      server ! ServiceActor.Start(config)
      tcpProbe.expectMsg(Tcp.Bind(server, localAddress))
      socketProbe.send(tcpProbe.sender(), Tcp.Bound(localAddress))
      expectMsg(ServiceActor.Started)
    }

    def expectClientConnection(): MockClientConnection = {
      val remoteAddress = new InetSocketAddress("example.com", Random.nextInt(65000))
      val connectionProbe = TestProbe()
      connectionProbe.send(server, Tcp.Connected(remoteAddress, localAddress))
      val handler = connectionProbe.expectMsgType[Tcp.Register].handler
      new MockClientConnection(remoteAddress, connectionProbe, handler)
    }
  }

  trait BoundServer extends FreshServer {
    expectSuccessfulBindOnStart()
  }
}
