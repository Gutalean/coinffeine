package coinffeine.overlay.relay

import scala.concurrent.duration._

import akka.testkit._
import akka.util.ByteString

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.DefaultTcpPortAllocator
import coinffeine.overlay.relay.client.{ClientConfig, RelayNetwork}
import coinffeine.overlay.relay.server.{ServerActor, ServerConfig}
import coinffeine.overlay.{OverlayId, OverlayNetwork}

class RelayNetworkIntegratedTest extends AkkaSpec {

  val connectionTimeout = 4.seconds.dilated
  val port = DefaultTcpPortAllocator.allocatePort()
  val server = system.actorOf(ServerActor.props, "server")
  val serverConfig = ServerConfig("localhost", port)
  val client = new RelayNetwork(system)
  val clientConfig = ClientConfig("localhost", port)

  "A relay network" should "bind to a port" in {
    server ! ServiceActor.Start(serverConfig)
    expectMsg(connectionTimeout, ServiceActor.Started)
  }

  it should "send messages forth and back" in {
    val client1 = system.actorOf(client.clientProps(clientConfig), "client1")
    client1 ! OverlayNetwork.Join(OverlayId(1))
    expectMsgAllClassOf(classOf[OverlayNetwork.Joined], classOf[OverlayNetwork.NetworkStatus])

    val client2 = system.actorOf(client.clientProps(clientConfig), "client2")
    client2 ! OverlayNetwork.Join(OverlayId(2))
    expectMsgAllClassOf(classOf[OverlayNetwork.Joined], classOf[OverlayNetwork.NetworkStatus])

    client1 ! OverlayNetwork.SendMessage(OverlayId(2), ByteString("ping"))
    client2 ! OverlayNetwork.SendMessage(OverlayId(1), ByteString("pong"))
    expectMsgAllOf(
      OverlayNetwork.ReceiveMessage(OverlayId(1), ByteString("ping")),
      OverlayNetwork.ReceiveMessage(OverlayId(2), ByteString("pong"))
    )

    client1 ! OverlayNetwork.Leave
    client2 ! OverlayNetwork.Leave
    expectMsgAllOf(
      OverlayNetwork.Leaved(OverlayId(1), OverlayNetwork.RequestedLeave),
      OverlayNetwork.Leaved(OverlayId(2), OverlayNetwork.RequestedLeave)
    )
  }

  it should "shut down gracefully" in {
    server ! ServiceActor.Stop
    expectMsg(connectionTimeout, ServiceActor.Stopped)
  }

  it should "fail to bind to a non-existent address" in {
    server ! ServiceActor.Start(serverConfig.copy(bindAddress = "does.not.exist.example.com"))
    expectMsgType[ServiceActor.StartFailure](connectionTimeout)
  }
}
