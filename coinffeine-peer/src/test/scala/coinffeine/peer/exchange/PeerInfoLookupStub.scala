package coinffeine.peer.exchange

import scala.concurrent.Future

import akka.actor.ActorContext

import coinffeine.model.exchange.Exchange
import coinffeine.model.exchange.Exchange.PeerInfo

class PeerInfoLookupStub extends PeerInfoLookup {

  private var result: Future[PeerInfo] = _

  def willSucceed(peerInfo: Exchange.PeerInfo): Unit = {
    result = Future.successful(peerInfo)
  }

  def willFail(reason: Throwable): Unit = {
    result = Future.failed(reason)
  }

  override def lookup()(implicit context: ActorContext): Future[PeerInfo] = result
}
