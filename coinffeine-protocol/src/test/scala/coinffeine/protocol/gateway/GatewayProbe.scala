package coinffeine.protocol.gateway

import scala.concurrent.duration.{Duration, FiniteDuration}

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import org.scalatest.Assertions

import coinffeine.model.network.{BrokerId, NodeId, PeerId}
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

/** Probe specialized on mocking a MessageGateway. */
class GatewayProbe(brokerId: PeerId)(implicit system: ActorSystem) extends Assertions {

  /** Underlying probe used for poking actors. */
  private val probe = TestProbe()

  /** Mapping of subscriptions used to relay only what is subscribed or fail otherwise. */
  private var subscriptions: Map[ActorRef, Set[ReceiveFilter]] = Map.empty

  def ref = probe.ref

  def expectSubscription(): Subscribe =
    expectSubscription(probe.testKitSettings.DefaultTimeout.duration)

  def expectSubscription(timeout: Duration): Subscribe = {
    val subscription = probe.expectMsgPF(timeout, hint = "expected subscription") {
      case s: Subscribe => s
    }
    val currentSubscription = subscriptions.getOrElse(probe.sender(), Set.empty)
    subscriptions = subscriptions.updated(probe.sender(), currentSubscription + subscription.filter)
    subscription
  }

  def expectUnsubscription(timeout: FiniteDuration): Unsubscribe.type =
    probe.expectMsg(timeout, hint = "expected unsubscription to broker", Unsubscribe)

  def expectUnsubscription(): Unsubscribe.type =
    expectUnsubscription(probe.testKitSettings.DefaultTimeout.duration)

  def expectForwardingToBroker(payload: Any, timeout: Duration = Duration.Undefined): Unit =
    expectForwarding(payload, brokerId, timeout)

  def expectForwarding(payload: Any, dest: NodeId, timeout: Duration = Duration.Undefined): Unit =
    probe.expectMsgPF(timeout) {
      case message @ ForwardMessage(`payload`, `dest`) => message
      case message @ ForwardMessage(`payload`, BrokerId) if isBroker(dest) => message
    }

  def expectForwardingPF[T](dest: NodeId, timeout: Duration = Duration.Undefined)
                           (payloadMatcher: PartialFunction[Any, T]): T =
    probe.expectMsgPF(timeout) {
      case ForwardMessage(payload, `dest`) if payloadMatcher.isDefinedAt(payload) =>
        payloadMatcher.apply(payload)
      case ForwardMessage(payload, BrokerId)
          if isBroker(dest) && payloadMatcher.isDefinedAt(payload) =>
        payloadMatcher.apply(payload)
    }

  def expectNoMsg(): Unit = probe.expectNoMsg()

  def expectNoMsg(timeout: FiniteDuration): Unit = probe.expectNoMsg(timeout)

  def isBroker(nodeId: NodeId): Boolean = nodeId == BrokerId || nodeId == brokerId

  /** Relay a message to subscribed actors or make the test fail if none is subscribed. */
  def relayMessage(message: PublicMessage, origin: NodeId): Unit = {
    val notification = ReceiveMessage(message, origin)
    val targets = for {
      (ref, filters) <- subscriptions.toSet
      filter <- filters if filter.isDefinedAt(notification)
    } yield ref
    assert(targets.nonEmpty, s"No one is expecting $notification, check subscription filters")
    targets.foreach { target =>
      probe.send(target, notification)
    }
  }

  def relayMessageFromBroker(message: PublicMessage): Unit = relayMessage(message, BrokerId)
}
