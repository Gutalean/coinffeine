package coinffeine.protocol.gateway

import scala.concurrent.duration._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.PeerId
import coinffeine.protocol.messages.PublicMessage

class MessageForwarderTest extends AkkaSpec {

  import MessageForwarder._

  "A message forwarder" should "forward a message" in new Fixture {
    fw ! Forward(SomeRequestMessage, somePeerId) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.relayMessage(SomeResponseMessage("Hello World!"), somePeerId)

    expectMsg("Hello World!")
    gw.expectUnsubscription()
  }

  it should "retry to forward when no response is received" in new Fixture {
    fw ! Forward(SomeRequestMessage, somePeerId, timeout = 500.millis) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second)

    gw.relayMessage(SomeResponseMessage("Hello World!"), somePeerId)

    expectMsg("Hello World!")
    gw.expectUnsubscription()
  }

  it should "retry to forward until max retries" in new Fixture {
    fw ! Forward(SomeRequestMessage, somePeerId, timeout = 500.millis, maxRetries = 2) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second)

    expectMsg(MessageForwarder.ConfirmationFailed(SomeRequestMessage))
    gw.expectUnsubscription()
  }

  it should "ignore forwarding requests once processing one" in new Fixture {
    fw ! Forward(SomeRequestMessage, somePeerId, timeout = 500.millis, maxRetries = 0) {
      case SomeResponseMessage(data) => data
    }
    fw ! MessageForwarder.Forward(SomeRequestMessage, somePeerId) {
      case SomeOtherResponseMessage(num) => num
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.relayMessage(SomeOtherResponseMessage(7), somePeerId, checkSubscriptions = false)
    gw.relayMessage(SomeResponseMessage("Hello World!"), somePeerId)

    expectMsg("Hello World!")
    gw.expectUnsubscription()
  }

  trait Fixture {
    val brokerId = PeerId("broker")
    val somePeerId = PeerId("some-peer")
    val gw = new GatewayProbe(brokerId)
    val fw = system.actorOf(MessageForwarder.props(gw.ref))
  }

  case object SomeRequestMessage extends PublicMessage
  case class SomeResponseMessage(data: String) extends PublicMessage
  case class SomeOtherResponseMessage(data: Int) extends PublicMessage
}
