package coinffeine.protocol.gateway

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe

import coinffeine.model.network.PeerId

class LinkedMessageGateways(brokerId: PeerId, leftId: PeerId, rightId: PeerId)
                           (implicit system: ActorSystem) {

  private val subscriptionsProbe = TestProbe()

  private val left = new AbstractMockGateway(brokerId, system) {

    override protected def subscriptionsRecipient = subscriptionsProbe.ref
    override protected def messagesRecipient = actor(new Act {
      become {
        case MessageGateway.ForwardMessage(msg, `rightId`) =>
          rightGateway ! MockGateway.Relay(MessageGateway.ReceiveMessage(msg, leftId))
      }
    })
  }

  private val right = new AbstractMockGateway(brokerId, system) {

    override protected def subscriptionsRecipient = subscriptionsProbe.ref
    override protected def messagesRecipient = actor(new Act {
      become {
        case MessageGateway.ForwardMessage(msg, `leftId`) =>
          leftGateway ! MockGateway.Relay(MessageGateway.ReceiveMessage(msg, rightId))
      }
    })
  }

  def leftGateway: ActorRef = left.ref
  def rightGateway: ActorRef = right.ref
}
