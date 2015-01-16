package coinffeine.overlay.relay

import scala.concurrent.duration._

import akka.testkit._
import akka.util.ByteString

import coinffeine.common.akka.ServiceActor
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.common.test.DefaultTcpPortAllocator
import coinffeine.overlay.relay.client.RelayNetwork
import coinffeine.overlay.relay.server.ServerActor
import coinffeine.overlay.relay.settings.{RelayServerSettings, RelayClientSettings}
import coinffeine.overlay.{OverlayId, OverlayNetwork}

class RelayNetworkIntegratedTest extends AkkaSpec {

  val connectionTimeout = 4.seconds.dilated
  val port = DefaultTcpPortAllocator.allocatePort()
  val serverConfig = RelayServerSettings("localhost", port)
  val server = system.actorOf(ServerActor.props, "server")
  val client = new RelayNetwork(RelayClientSettings("localhost", port), system)

  "A relay network" should "bind to a port" in {
    server ! ServiceActor.Start(serverConfig)
    expectMsg(connectionTimeout, ServiceActor.Started)
  }

  it should "send messages forth and back" in {
    val client1 = system.actorOf(client.clientProps, "client1")
    client1 ! OverlayNetwork.Join(OverlayId(1))
    expectMsgType[OverlayNetwork.Joined]

    val client2 = system.actorOf(client.clientProps, "client2")
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
