package coinffeine.benchmark.action

import akka.actor.{ActorRef, Props}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.akka.GatlingActorSystem
import io.gatling.core.config.Protocols
import io.gatling.core.session.Expression

import coinffeine.benchmark.config.CoinffeineProtocol
import coinffeine.protocol.messages.PublicMessage

case class AskRequestBuilder(
    requestName: Expression[String],
    message: PublicMessage,
    responseMatcher: AskRequest.ResponseMatcher = AskRequest.DefaultResponseMatcher)
  extends ActionBuilder {

  override def build(next: ActorRef, protocols: Protocols) = {
    val proto = protocols.getProtocol[CoinffeineProtocol]
    require(proto.isDefined)
    GatlingActorSystem.instance.actorOf(Props(new AskRequest(
      requestName, next, proto.get, message, responseMatcher)))
  }

  def response(matcher: AskRequest.ResponseMatcher) = copy(responseMatcher = matcher)
}
