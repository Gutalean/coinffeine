package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.{NodeId, PeerId}
import coinffeine.protocol.messages.PublicMessage

class MessageForwarderTest extends AkkaSpec {

  import MessageForwarder._

  "A message forwarder" should "forward a message" in new Fixture {
    val fw = forwarder(SomeRequestMessage, somePeerId) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.relayMessage(SomeResponseMessage("Hello World!"), somePeerId)

    expectMsg("Hello World!")
  }

  it should "retry to forward when no response is received" in new Fixture {
    val fw = forwarder(SomeRequestMessage, somePeerId, RetrySettings(timeout = 500.millis.dilated)) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second.dilated)

    gw.relayMessage(SomeResponseMessage("Hello World!"), somePeerId)

    expectMsg("Hello World!")
  }

  it should "retry to forward until max retries" in new Fixture {
    val fw = forwarder(
        SomeRequestMessage, somePeerId, RetrySettings(timeout = 500.millis.dilated, maxRetries = 2)) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, somePeerId)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second.dilated)
    gw.expectForwarding(SomeRequestMessage, somePeerId, 1.second.dilated)

    expectMsg(MessageForwarder.ConfirmationFailed(SomeRequestMessage))
  }

  trait Fixture {
    val brokerId = PeerId.hashOf("broker")
    val somePeerId = PeerId.hashOf("some-peer")
    val gw = new MockGateway(brokerId)

    def forwarder[A](message: PublicMessage, destination: NodeId,
                     retry: RetrySettings = DefaultRetrySettings)
                    (confirmation: PartialFunction[PublicMessage, A]): ActorRef = {
      system.actorOf(MessageForwarder.props(self, gw.ref, message, destination, confirmation, retry))
    }
  }

  case object SomeRequestMessage extends PublicMessage
  case class SomeResponseMessage(data: String) extends PublicMessage
  case class SomeOtherResponseMessage(data: Int) extends PublicMessage
}
