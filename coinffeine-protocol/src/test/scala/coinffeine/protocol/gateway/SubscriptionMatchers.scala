package coinffeine.protocol.gateway

import org.scalatest.matchers.{MatchResult, Matcher}

import coinffeine.model.network.{NodeId, PeerId}
import coinffeine.protocol.gateway.MessageGateway.ReceiveMessage
import coinffeine.protocol.messages.PublicMessage

trait SubscriptionMatchers {
  def subscribeTo(message: PublicMessage, from: NodeId) =
    Matcher[MessageGateway.Subscribe] { subscription =>
      MatchResult(
        matches = subscription.filter.isDefinedAt(ReceiveMessage(message, from)),
        rawFailureMessage = s"subscription does not match $message from $from",
        rawNegatedFailureMessage = s"subscription matches $message from $from"
      )
    }

  def subscribeTo(message: PublicMessage) =
    Matcher[MessageGateway.SubscribeToBroker] { subscription =>
      MatchResult(
        matches = subscription.filter.isDefinedAt(message),
        rawFailureMessage = s"subscription does not match $message",
        rawNegatedFailureMessage = s"subscription matches $message"
      )
    }
}
