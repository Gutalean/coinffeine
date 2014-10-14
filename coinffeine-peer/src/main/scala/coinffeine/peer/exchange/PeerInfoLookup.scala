package coinffeine.peer.exchange

import scala.concurrent.Future

import akka.actor.ActorContext

import coinffeine.model.exchange.Exchange

trait PeerInfoLookup {
  /** Lookup current peer info */
  def lookup()(implicit context: ActorContext): Future[Exchange.PeerInfo]
}
