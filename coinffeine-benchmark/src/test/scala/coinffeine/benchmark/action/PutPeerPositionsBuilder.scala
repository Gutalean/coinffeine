package coinffeine.benchmark.action

import akka.actor.{Props, ActorRef}
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.akka.GatlingActorSystem
import io.gatling.core.config.Protocols
import io.gatling.core.session.Expression

import coinffeine.benchmark.config.CoinffeineProtocol
import coinffeine.model.currency.Euro
import coinffeine.model.market.OrderBookEntry

case class PutPeerPositionsBuilder(
    requestName: Expression[String],
    orderBookEntries: Seq[OrderBookEntry[Euro.type]] = PutPeerPositions.DefaultOrderBookEntries)
  extends ActionBuilder {

  override def build(next: ActorRef, protocols: Protocols) = {
    val proto = protocols.getProtocol[CoinffeineProtocol]
    require(proto.isDefined)
    GatlingActorSystem.instance.actorOf(Props(new PutPeerPositions(
      requestName, next, proto.get, orderBookEntries)))

  }

  def orderBookEntries(entries: Seq[OrderBookEntry[Euro.type]]) = copy(orderBookEntries = entries)
}
