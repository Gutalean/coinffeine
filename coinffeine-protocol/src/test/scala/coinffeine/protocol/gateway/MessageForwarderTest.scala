package coinffeine.protocol.gateway

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.testkit._

import coinffeine.common.akka.test.AkkaSpec
import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageForwarder.RetrySettings
import coinffeine.protocol.gateway.MessageGateway.ForwardMessage
import coinffeine.protocol.messages.PublicMessage

class MessageForwarderTest extends AkkaSpec {

  val destination = PeerId.hashOf("some-peer")

  "A message forwarder" should "forward a message" in new Fixture {
    val fw = forwarder(SomeRequestMessage, timeout = 3.seconds.dilated) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, destination)

    gw.relayMessage(SomeResponseMessage("Hello World!"), destination)
    expectMsg("Hello World!")
  }

  it should "retry to forward when no response is received" in new Fixture {
    val fw = forwarder(SomeRequestMessage) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, destination)
    gw.expectForwarding(SomeRequestMessage, destination)

    gw.relayMessage(SomeResponseMessage("Hello World!"), destination)
    expectMsg("Hello World!")
  }

  it should "retry to forward until max retries" in new Fixture {
    val fw = forwarder(SomeRequestMessage, retries = 2) {
      case SomeResponseMessage(data) => data
    }

    gw.expectSubscription()
    gw.expectForwarding(SomeRequestMessage, destination)
    gw.expectForwarding(SomeRequestMessage, destination)
    gw.expectForwarding(SomeRequestMessage, destination)

    expectMsg(MessageForwarder.ConfirmationFailed(SomeRequestMessage))
  }

  trait Fixture {
    val gw = new MockGateway()

    def forwarder[A](message: PublicMessage,
                     timeout: FiniteDuration = 100.millis.dilated,
                     retries: Int = Integer.MAX_VALUE)
                    (confirmation: PartialFunction[PublicMessage, A]): ActorRef =
      system.actorOf(MessageForwarder.props(self, gw.ref, ForwardMessage(message, destination),
        confirmation, RetrySettings(timeout, retries)))
  }

  case object SomeRequestMessage extends PublicMessage
  case class SomeResponseMessage(data: String) extends PublicMessage
  case class SomeOtherResponseMessage(data: Int) extends PublicMessage
}
