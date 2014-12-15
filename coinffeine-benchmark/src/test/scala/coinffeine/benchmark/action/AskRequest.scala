package coinffeine.benchmark.action

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout
import io.gatling.core.action.Chainable
import io.gatling.core.session._

import coinffeine.benchmark.config.CoinffeineProtocol
import coinffeine.model.network.BrokerId
import coinffeine.protocol.messages.PublicMessage

class AskRequest(val requestName: Expression[String],
                 val next: ActorRef,
                 protocol: CoinffeineProtocol,
                 request: PublicMessage,
                 responseMatcher: AskRequest.ResponseMatcher)
    extends Chainable with ReportingSupport {

  override def runAction() = {
    implicit val timeout = Timeout(10.seconds)
    protocol.ask(request, BrokerId, responseMatcher)
  }
}

object AskRequest {

  type ResponseMatcher = PartialFunction[PublicMessage, Unit]

  val DefaultResponseMatcher: ResponseMatcher = {
    case _: PublicMessage =>
  }
}
