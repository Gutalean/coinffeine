package coinffeine.protocol.gateway

import scala.concurrent.duration.{Duration, FiniteDuration}

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import org.scalatest.Assertions

import coinffeine.model.network.PeerId
import coinffeine.protocol.gateway.MessageGateway._
import coinffeine.protocol.messages.PublicMessage

/** Probe specialized on mocking a MessageGateway. */
class GatewayProbe(brokerId: PeerId)(implicit system: ActorSystem) extends Assertions {

  /** Underlying probe used for poking actors. */
  private val probe = TestProbe()

  /** Mapping of subscriptions used to relay only what is subscribed or fail otherwise. */
  private var subscriptions: Map[ActorRef, Set[ReceiveFilter]] = Map.empty
  private var brokerSubscriptions: Map[ActorRef, Set[MessageFilter]] = Map.empty

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

  def expectSubscriptionToBroker(): SubscribeToBroker =
    expectSubscriptionToBroker(probe.testKitSettings.DefaultTimeout.duration)

  def expectSubscriptionToBroker(timeout: Duration): SubscribeToBroker = {
    val subscription =probe.expectMsgPF(timeout, hint = "expected subscription to broker") {
      case s: SubscribeToBroker => s
    }
    val currentSubscription = brokerSubscriptions.getOrElse(probe.sender(), Set.empty)
    brokerSubscriptions =
      brokerSubscriptions.updated(probe.sender(), currentSubscription + subscription.filter)
    subscription
  }

  def expectForwarding(payload: Any, dest: PeerId, timeout: Duration = Duration.Undefined): Unit =
    probe.expectMsgPF(timeout) {
      case message @ ForwardMessage(`payload`, `dest`) => message
    }

  def expectForwardingPF[T](dest: PeerId, timeout: Duration = Duration.Undefined)
                           (payloadMatcher: PartialFunction[Any, T]): T =
    probe.expectMsgPF(timeout) {
      case ForwardMessage(payload, `dest`) if payloadMatcher.isDefinedAt(payload) =>
        payloadMatcher.apply(payload)
    }

  def expectNoMsg(): Unit = probe.expectNoMsg()

  def expectNoMsg(timeout: FiniteDuration): Unit = probe.expectNoMsg(timeout)

  /** Relay a message to subscribed actors or make the test fail if none is subscribed. */
  def relayMessage(message: PublicMessage, origin: PeerId): Unit = {
    val notification = ReceiveMessage(message, origin)
    val subscriptionTargets = for {
      (ref, filters) <- subscriptions.toSet
      filter <- filters if filter.isDefinedAt(notification)
    } yield ref
    val brokerSubscriptionTargets =
      if (origin != brokerId) Set.empty
      else for {
        (ref, filters) <- brokerSubscriptions.toSet
        filter <- filters if filter.isDefinedAt(notification.msg)
      } yield ref
    val targets = subscriptionTargets ++ brokerSubscriptionTargets
    assert(targets.nonEmpty, s"No one is expecting $notification, check subscription filters")
    targets.foreach { target =>
      probe.send(target, notification)
    }
  }

  def relayMessageFromBroker(message: PublicMessage): Unit = {
    relayMessage(message, brokerId)
  }
}
