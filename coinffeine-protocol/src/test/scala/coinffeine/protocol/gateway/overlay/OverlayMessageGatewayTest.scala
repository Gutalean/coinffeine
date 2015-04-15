package coinffeine.protocol.gateway.overlay

import scala.concurrent.duration._

import akka.actor._
import akka.testkit._
import org.scalatest.concurrent.Eventually

import coinffeine.alarms.akka.AlarmMessage
import coinffeine.common.akka.Service
import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.Both
import coinffeine.model.currency._
import coinffeine.model.exchange.ExchangeId
import coinffeine.model.market.OrderId
import coinffeine.model.network._
import coinffeine.overlay.OverlayId
import coinffeine.protocol.gateway.MessageGateway
import coinffeine.protocol.gateway.MessageGateway.{ProtocolMismatchAlarm, Subscribe}
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.brokerage.OrderMatch
import coinffeine.protocol.serialization.ProtocolMismatch
import coinffeine.protocol.serialization.test.TestProtocolSerializationComponent
import coinffeine.protocol.{MessageGatewaySettings, Version}

class OverlayMessageGatewayTest
  extends AkkaSpec(AkkaSpec.systemWithLoggingInterception("overlayGateway")) with Eventually
  with TestProtocolSerializationComponent with IdConversions {

  val overlayId = OverlayId(1)
  val sampleOrderMatch = OrderMatch(OrderId.random(), ExchangeId.random(),
    Both.fill(1.BTC), Both.fill(300.EUR), lockTime = 42, counterpart = PeerId.random())
  val idleTime = 100.millis.dilated

  "An overlay message gateway" should "try to join on start" in new FreshGateway {
    expectSuccessfulStart()
    overlay.expectJoinAs(overlayId)
  }

  it should "retry connecting when join attempt fails" in new FreshGateway {
    expectSuccessfulStart()
    overlay.expectJoinAs(overlayId)
    overlay.rejectJoin()
    expectSuccessfulJoin()
  }

  it should "retry connecting after a random disconnection" in new JoinedGateway {
    overlay.givenRandomDisconnection()
    overlay.expectJoinAs(overlayId)
  }

  it should "deliver incoming messages to subscribers" in new FreshGateway {
    gateway ! Subscribe {
      case MessageGateway.ReceiveMessage(_: OrderMatch[_], _) =>
    }
    expectSuccessfulStart()
    val sender = PeerId.random()
    expectSuccessfulJoin()
    overlay.receiveFrom(sender, sampleOrderMatch)
    expectMsg(MessageGateway.ReceiveMessage(sampleOrderMatch, sender))
  }

  it should "forward outgoing messages to the overlay network" in new JoinedGateway {
    val dest = PeerId.random()
    gateway ! MessageGateway.ForwardMessage(sampleOrderMatch, dest)
    overlay.expectSendTo(dest, sampleOrderMatch)
  }

  it should "log messages whose serialization fail" in new JoinedGateway {
    object InvalidMessage extends PublicMessage
    EventFilter[Throwable](start = "Cannot serialize message", occurrences = 1) intercept {
      gateway ! MessageGateway.ForwardMessage(InvalidMessage, PeerId.random())
    }
  }

  it should "log invalid messages received" in new JoinedGateway {
    EventFilter[Throwable](start = "Dropping invalid incoming message", occurrences = 1) intercept {
      overlay.receiveInvalidMessageFrom(BrokerId)
    }
  }

  it should "reply with a protocol mismatch to messages of other protocol versions" in
    new JoinedGateway {
      val sender = PeerId.random()
      val version = Version(major = 42, minor = 13)
      overlay.receiveMessageOfProtocolVersion(version, sender)
      overlay.expectSendTo(sender, ProtocolMismatch(Version.Current))
    }

  it should "raise an alarm when a protocol mismatch is received from the broker" in
    new JoinedGateway {
      val brokerVersion = Version(major = 100, minor = 0)
      system.eventStream.subscribe(self, classOf[AlarmMessage.Alert])
      overlay.receiveFrom(BrokerId, ProtocolMismatch(brokerVersion))
      expectMsg(AlarmMessage.Alert(ProtocolMismatchAlarm(Version.Current, brokerVersion)))
    }

  it should "leave before stopping" in new JoinedGateway {
    gateway ! Service.Stop
    overlay.expectLeave()
    overlay.acceptLeave()
    expectMsg(Service.Stopped)
  }

  it should "wait for the join attempt and then leave before stopping" in new FreshGateway {
    expectSuccessfulStart()
    overlay.expectJoinAs(overlayId)

    gateway ! Service.Stop
    expectNoMsg(idleTime)

    overlay.acceptJoin(5)
    overlay.expectLeave()
    overlay.acceptLeave()
    expectMsg(Service.Stopped)
  }

  it should "wait for a failed join attempt and then stop right away" in new FreshGateway {
    expectSuccessfulStart()
    overlay.expectJoinAs(overlayId)

    gateway ! Service.Stop
    expectNoMsg(idleTime)

    overlay.rejectJoin()
    expectMsg(Service.Stopped)
  }

  it should "stop immediately when waiting to rejoin" in new FreshGateway {
    expectSuccessfulStart()
    overlay.expectJoinAs(overlayId)
    overlay.rejectJoin()
    expectNoMsg(idleTime)

    gateway ! Service.Stop
    expectMsg(Service.Stopped)
  }

  trait FreshGateway {
    private val settings = MessageGatewaySettings(
      peerId = PeerId("0" * 19 + "1"),
      connectionRetryInterval = 1.second.dilated
    )
    val overlay = new MockOverlayNetwork(protocolSerialization)
    val properties = new MutableCoinffeineNetworkProperties
    val gateway = system.actorOf(Props(
      new OverlayMessageGateway(settings, overlay, protocolSerialization, properties)))

    def expectSuccessfulStart(): Unit = {
      gateway ! Service.Start {}
      expectMsg(Service.Started)
      overlay.expectClientSpawn()
    }

    def expectSuccessfulJoin(): Unit = {
      overlay.expectJoinAs(overlayId)
      overlay.acceptJoin(networkSize = 3)
      eventually {
        properties.activePeers.get shouldBe 2
      }
    }
  }

  trait JoinedGateway extends FreshGateway {
    expectSuccessfulStart()
    expectSuccessfulJoin()
  }
}
