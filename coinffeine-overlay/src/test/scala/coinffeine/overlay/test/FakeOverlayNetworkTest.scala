package coinffeine.overlay.test

import akka.util.ByteString

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.overlay.OverlayId
import coinffeine.overlay.OverlayNetwork._

class FakeOverlayNetworkTest extends AkkaSpec {

  "An overlay network" should "relay messages forth and back" in {
    val network = FakeOverlayNetwork()
    val client1, client2 = system.actorOf(network.defaultClientProps)

    client1 ! Join(OverlayId(1))
    client2 ! Join(OverlayId(2))
    expectMsgAllOf(Joined(OverlayId(1)), Joined(OverlayId(2)))

    client1 ! SendMessage(OverlayId(2), ByteString("ping"))
    expectMsg(ReceiveMessage(OverlayId(1), ByteString("ping")))
    lastSender shouldBe client2

    client2 ! SendMessage(OverlayId(1), ByteString("pong"))
    expectMsg(ReceiveMessage(OverlayId(2), ByteString("pong")))
    lastSender shouldBe client1

    client1 ! Leave
    client2 ! Leave
    expectMsgAllOf(Leaved(OverlayId(1), RequestedLeave), Leaved(OverlayId(2), RequestedLeave))
  }
}
