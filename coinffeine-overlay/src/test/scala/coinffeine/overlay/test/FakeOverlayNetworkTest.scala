package coinffeine.overlay.test

import scala.concurrent.duration._

import akka.testkit._
import akka.util.ByteString

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.overlay.OverlayId
import coinffeine.overlay.OverlayNetwork._

class FakeOverlayNetworkTest extends AkkaSpec {

  "An overlay network" should "relay messages forth and back" in {
    ignoreMsg {
      case NetworkStatus(_) => true
    }
    val network = FakeOverlayNetwork()
    val client1, client2 = system.actorOf(network.clientProps)

    client1 ! Join(OverlayId(1))
    expectMsg(Joined(OverlayId(1), NetworkStatus(1)))

    client2 ! Join(OverlayId(2))
    expectMsg(Joined(OverlayId(2), NetworkStatus(2)))

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

  it should "drop messages according to a rate" in {
    val network = FakeOverlayNetwork(messageDroppingRate = 0.5)
    val client1, client2 = system.actorOf(network.clientProps)
    client1 ! Join(OverlayId(1))
    client2 ! Join(OverlayId(2))
    expectMsgAllClassOf(classOf[Joined], classOf[Joined])


    for (i <- 1 to 100) {
      client1 ! SendMessage(OverlayId(2), ByteString("maybe"))
    }
    val messageCount = receiveWhile(3.seconds.dilated) {
      case message: ReceiveMessage => message
    }.size
    messageCount should (be > 5 and be < 95)
  }

  it should "fail to connect according to a rate" in {
    val network = FakeOverlayNetwork(connectionFailureRate = 0.5)
    val client = system.actorOf(network.clientProps)

    var failedConnections = 0
    for (i <- 1 to 100) {
      client ! Join(OverlayId(1))
      expectMsgAnyClassOf(classOf[Joined], classOf[JoinFailed]) match {
        case _: Joined =>
          client ! Leave
          expectMsgType[Leaved]
        case _: JoinFailed =>
          failedConnections += 1
      }
    }
    failedConnections should (be > 5 and be < 95)
  }

  it should "delay the messages according to a distribution of times" in {
    val network = FakeOverlayNetwork(
      delayDistribution = new FakeOverlayNetwork.ExponentialDelay(500.millis.dilated))
    val sender, receiver = system.actorOf(network.clientProps)
    sender ! Join(OverlayId(1))
    receiver ! Join(OverlayId(2))
    expectMsgAllClassOf(classOf[Joined], classOf[Joined])

    for (i <- 1 to 100) {
      sender ! SendMessage(OverlayId(2), ByteString(i.toByte))
    }
    measureTime {
      receiveWhile(messages = 100) {
        case message: ReceiveMessage =>
      }
    } should be < 5.seconds.dilated
  }

  it should "randomly drop connections according to a distribution of times" in {
    val network = FakeOverlayNetwork(
      disconnectionDistribution = new FakeOverlayNetwork.ExponentialDelay(100.millis.dilated))
    val client = system.actorOf(network.clientProps)
    val id = OverlayId(1)
    client ! Join(id)
    expectMsgType[Joined]
    fishForMessage(max = 1.second.dilated) {
      case Leaved(`id`, _) => true
      case _ => false
    }
  }

  private def measureTime(block: => Unit): FiniteDuration = {
    val startTime = System.currentTimeMillis()
    block
    (System.currentTimeMillis() - startTime).millis
  }
}
