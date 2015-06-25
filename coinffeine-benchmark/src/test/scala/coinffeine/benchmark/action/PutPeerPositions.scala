package coinffeine.benchmark.action

import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.util.Timeout
import io.gatling.core.action.Chainable
import io.gatling.core.session._

import coinffeine.benchmark.config.CoinffeineProtocol
import coinffeine.model.currency.Euro
import coinffeine.model.market.{Market, OrderBookEntry}
import coinffeine.model.network.BrokerId
import coinffeine.protocol.messages.PublicMessage
import coinffeine.protocol.messages.brokerage.{PeerPositions, PeerPositionsReceived}

class PutPeerPositions(val requestName: Expression[String],
                       val next: ActorRef,
                       protocol: CoinffeineProtocol,
                       orderBookEntries: Seq[OrderBookEntry])
    extends Chainable with ReportingSupport {

  override def runAction() = {
    implicit val timeout = Timeout(10.seconds)
    val request = PeerPositions(Market(Euro), orderBookEntries)
    val responseMatcher: PartialFunction[PublicMessage, Unit] = {
      case PeerPositionsReceived(nonce) if nonce == request.nonce =>
    }
    protocol.ask(request, BrokerId, responseMatcher)
  }
}

object PutPeerPositions {

  val DefaultOrderBookEntries: Seq[OrderBookEntry] = Seq.empty
}
